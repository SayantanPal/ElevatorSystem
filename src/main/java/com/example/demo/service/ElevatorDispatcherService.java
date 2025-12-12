package com.example.demo.service;

import com.example.demo.IConstants;
import com.example.demo.cache.ElevatorCache;
import com.example.demo.cache.UserRequestCache;
import com.example.demo.enums.*;
import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;
import com.example.demo.repository.ElevatorRepository;
import com.example.demo.scheduler.ElevatorScheduler;
import com.example.demo.scheduler.SCANScheduler;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
* ElevatorManagerService should read elevators from the shared cache (or repository)
* and schedule a periodic task for each elevator (and allow dynamic registration for later-created elevators).
* */

@Getter
public class ElevatorDispatcherService  implements Serializable {// ElevatorDispatcherService or ElevatorRequestService

    // Instance-level mutable state - state change is involved - hence non-static below
    private final ElevatorScheduler scheduler;
    private final ElevatorRepository elevatorRepository;
    private final ElevatorMovementService1 elevatorMovementService;
    private static final int MAX_RETRIES = 6;

    // Static utility components (shared, not business state) - Read only dependency; Not state change
    private static final Logger LOGGER = LoggerFactory.getLogger(ElevatorDispatcherService.class);


    public ElevatorDispatcherService() {
        this.scheduler = new SCANScheduler();
        this.elevatorMovementService = ElevatorMovementService1.getInstance();
        this.elevatorRepository  = new ElevatorRepository();
    }

    // Passenger Requesting for both Pick-up & Drop-off
    // 3. In case of global Destination Floor Selection, algo needs to decide which elevator to map to
    public void selectDestinationFloorOutsideForBothPickUpAndDropOff(int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        ElevatorRequest request = new ElevatorRequest(requestPriority, requestedFromFloor, toDestFloor);
        // scheduler determines which is best elevator to assign as per floor req
        // and then assigns request to that particular elevator
        this.assignRequestToElevator(request);
    }

    // You may need a background worker thread to retry pending requests.

    private Elevator assignRequestToElevator(ElevatorRequest request) {

        // MULTIPLE controller/HTTP threads can call outer assignRequestToElevator(request) at the same time
        // 2 different HTTP requests may assign the same elevator to two different pickups at the same time
        // So, Avoid Race Condition - Two threads selecting the same elevator for assignment simultaneously


        boolean hasAcquiredLock = false;
        int retryAttempt = 0;
        Elevator bestElevator = null;


        // Spin-Retry Pattern with fairness & circuit breaker fallback
        try{
            while(retryAttempt <= MAX_RETRIES) { // thread retry to find another elevator till it gets the lock on some elevator
                // Step-1. Selection of best elevator as per scheduling algo(read-heavy, short-lived operation)
                // Scheduler is read-only; it selects bestElevator based on current elevator state.
                // Scheduler has no concept of reservation; it can pick the same elevator for multiple selectors until the elevator is actually mutated.
                // SCANScheduler is fine for picking a elevator candidate, but it cannot guarantee the selected elevator remains suitable by the time previous request thread mutate elevator state in next assignment step
                // scheduler reads shared elevator state without locks and returns a candidate. Between that read and the later mutation (assignment), the elevator state can change. This is a classic TOCTOU (time-of-check → time-of-use) race.
                // That’s why reservation (tryLock while selecting) of elevator lock followed by reasonable timeout is needed
                // Scheduler determines which src/originating floor request is suitable to be mapped to which nearest elevator either idle or moving in same direction
                // helps to find optimal/nearest working/idle elevator or fallback elevator to pick up a user from requested legitimate floor
                bestElevator =  scheduler.findBestElevator(ElevatorCache.elevators, request);
                if(bestElevator == null){
                    LOGGER.info("No suitable elevator found to assign for request: {} because either the floor is invalid or all the elevators are in non-working state", request);
                    System.out.println(LocalDateTime.now() + " - No suitable elevator found to assign for request: " + request);
                    return null;
                } // when at least 1 elevator in working state and the floor is valid input


                // The lock acquisition is immediately after elevator selection, before assignment.
                // Even if another thread picked the same elevator a millisecond earlier, the current thread will fail tryLock() and re-run selection, avoiding simultaneous assignment to the same elevator
                // Fair lock to ensure FIFO order among waiting threads - ensure no starving threads

                // tryLock() can succeed but the elevator may have been changed between scheduler read and acquiring the lock (e.g., movement thread just released and changed state).
                // That's why If you have high concurrency, you might want slightly longer timeout, e.g., 10–50 ms, to reduce wasted CPU in tight retry loops.
                hasAcquiredLock = bestElevator.getLock().tryLock(50, TimeUnit.MILLISECONDS);
                if(hasAcquiredLock){
                    // updating the elevator state inside the lock (MOVING_UP / MOVING_DOWN) so other threads see the updated state next time they call scheduler.
                    // This ensures the scheduler will avoid assigning an already moving elevator unless aligned with the direction.

                    // Step-2. Assignment of request to that best elevator
                    // Perform the multi-step update while holding the elevator lock
                    this.assignRequestToElevator(request, bestElevator);
                    return bestElevator;
                }
                // If the chosen elevator’s lock is busy (another thread is mutating it), this thread immediately retries selection.
                retryAttempt++; // track fair retry attempt
                Thread.sleep(20L * retryAttempt); // Exponential backoff brief pause before retrying to avoid optimal elevator selection based on stale elevator state
            }
            // When thread Couldn't acquire per-elevator lock in time — handle fallback: requeue in buffer for another round of retry
            LOGGER.info("Max retries reached while trying to assign request: {} to an elevator. Please try again later.", request);
            System.out.println(LocalDateTime.now() + " - Max retries reached while trying to assign request: " + request + " to an elevator. Please try again later.");
            if (request.getIsEnqueued().compareAndSet(false, true)) {
                UserRequestCache.getPendingRequests().offer(request);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (request.getIsEnqueued().compareAndSet(false, true)) {
                UserRequestCache.getPendingRequests().offer(request);
            }
            return null;
        } finally {
            if (hasAcquiredLock && bestElevator.getLock().isHeldByCurrentThread()) {
                bestElevator.getLock().unlock();
            }
        }
        return bestElevator;
    }

    private void assignRequestToElevator(ElevatorRequest request, Elevator pickUpElevator) {

            // scheduled elevator should be assigned to go to that source floor for pickup
            // scheduled elevator relevant for only pick-up scenario in global floor call or global dest floor selection
            pickUpElevator.addFloor(request.getFromSrcFloor());
            if (request.getRequestType() == RequestType.DESTINATION_FLOOR_SELECTION) {
                pickUpElevator.addFloor(request.getToDestFloor()); // always add dest floor to same assigned elevator which picked up the user
                UserRequestCache.getActiveRequests().put(request.getRequestId(), request);
            }
            request.setRequestStatus(RequestStatus.ASSIGNED);
            request.getIsEnqueued().set(false);

//            UserRequestCache.getPendingRequests().remove(request);

            // set elevator state if it was idle
            if (pickUpElevator.isStandingIdle()) {
                int currentFloor = pickUpElevator.getCurrentFloor();
                ElevatorState newState = request.getFromSrcFloor() > currentFloor ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN;
                pickUpElevator.setElevatorState(newState);
            }

            // persist change
            this.elevatorRepository.save(pickUpElevator);
    }

    public void processPendingRequestsSafely() {
        try {
            processPendingRequests();
        } catch (Exception ex) {
            LOGGER.error("Unexpected error while retrying pending requests", ex);
        }
    }


    private void processPendingRequests() {
        int maxBatch = 10; // prevents starvation if queue is huge
        int processed = 0;

        while (processed < maxBatch && !UserRequestCache.getPendingRequests().isEmpty()) {
            ElevatorRequest request = UserRequestCache.getPendingRequests().poll();
            if (request == null) break;

            Elevator assignedElevator = assignRequestToElevator(request);
            if (assignedElevator == null) {
                // could not assign → put it back for later retry
                if (request.getIsEnqueued().compareAndSet(false, true)) {
                    UserRequestCache.getPendingRequests().offer(request);
                }
            }
            processed++;
        }
    }


    private static class ElevatorDispatcherServiceHolder {
//        @Serial
//        private static final long serialVersionUID = 1L;
        private static final ElevatorDispatcherService INSTANCE = new ElevatorDispatcherService();
    }

    public static ElevatorDispatcherService getInstance() {
        return ElevatorDispatcherServiceHolder.INSTANCE;
    }

    // This ensures deserialization returns the existing instance
    @Serial
    protected Object readResolve() {
        return getInstance();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Singleton — cannot clone");
    }
}
