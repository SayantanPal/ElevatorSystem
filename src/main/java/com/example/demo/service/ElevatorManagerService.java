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
import com.example.demo.utility.Helper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/*
* ElevatorManagerService should read elevators from the shared cache (or repository)
* and schedule a periodic task for each elevator (and allow dynamic registration for later-created elevators).
* */

@Getter
public class ElevatorManagerService implements Serializable {// ElevatorDispatcherService or ElevatorRequestService
//    private List<Elevator> elevators;
    private static final long serialVersionUID = 1L;

    // Instance-level mutable state - state change is involved - hence non-static below
    private final ElevatorScheduler scheduler;
    private final ElevatorRepository elevatorRepository;
    private final ElevatorMovementService elevatorMovementService;

    // Static utility components (shared, not business state) - Read only dependency; Not state change
    private static final Logger LOGGER = LoggerFactory.getLogger(ElevatorManagerService.class);
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR =
            Executors.newScheduledThreadPool(4);


    private ElevatorManagerService() {
//        this.elevators = new ArrayList<>();
        this.scheduler = new SCANScheduler();
        this.elevatorRepository = new ElevatorRepository();
        this.elevatorMovementService = ElevatorMovementService.getInstance();
        this.initElevator();

        // Background retry scheduler (centralized)
        SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                this::processAllPendingFloorRequests, 0, 1, TimeUnit.MINUTES
        );
    }

    // Static inner class responsible for holding the instance
    private static class Holder {
        private static final ElevatorManagerService INSTANCE = new ElevatorManagerService();
    }

    // Global access point
    public static ElevatorManagerService getInstance() {
        return ElevatorManagerService.Holder.INSTANCE;
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

    public void initElevator(){
        this.createElevator(IConstants.INITIAL_ELEVATOR_COUNT);
    }

    /*
    * createElevator() must add the elevator to repository/cache
    * and then inform ElevatorMovementService to schedule movement for the newly created elevator
    * */
    public Elevator createElevator(){
        Elevator e = new Elevator(ElevatorState.IDLE);
        ElevatorCache.elevators.add(e);
        this.elevatorRepository.save(e);
        this.elevatorMovementService.registerElevator(e);
        return e;
    }

    public List<Elevator> createElevator(int count){
        List<Elevator> created = new ArrayList<>();
        for(int i = 0; i < count; i++){
            created.add(this.createElevator());
        }
        return created;
    }

    /*
    * assignRequestToElevator performs a multi-step mutation across elevator state and caches
    * when there is multi-step state transitions (like add destination + change elevator state + persist)
    * then only atomic or thread-safe collections alone cannot help without locking
    * because though individual operations are thread-safe but the intermediate states can be observed by other threads.
    * */

    // synchronized keyword in method signature is never fine-grained; it is per object monitor blocking lock
    // but, both synchronized(elevator) and rentrant lock like elevator.getLock() is fine-grained as they lock per elevator object only
    //
    // synchronized(elevator) {} block scope is always a blocking lock acquisition
    // because if another thread already holds that lock, the current thread waits (blocks) until the lock becomes available. There’s no way to skip, timeout, or do other work in the meantime.
    //
    // manual lock like elevator.getLock().lock() — this too will block until the lock is available, but controllable
    // means either Non-blocking option → tryLock()  because: The thread checks if the lock is available. If it’s not, it moves on immediately (no waiting, no suspension).
    // or in-between mode Timed option → tryLock(timeout, unit) - That’s bounded blocking — you wait for some time, then move on
    // both bounded and non-blocking options allow doing other work or handling the unavailability gracefully instead of being stuck waiting/indefinitelu blocked by synchronized.

    private void assignRequestToElevator(ElevatorRequest request, Elevator elevator) {

        boolean locked = false;
            try{
                locked = elevator.getLock().tryLock(1, TimeUnit.SECONDS);
                if (!locked) {
                    // Couldn't acquire per-elevator lock in time — requeue or handle fallback
                    UserRequestCache.getPendingRequests().offer(request);
                    return;
                }

                // Perform the multi-step update while holding the elevator lock

                // If this is a floor call (user pressed up/down at a floor),
                // elevator should be assigned to go to that source floor for pickup.
                if (request.getRequestType() == RequestType.FLOOR_DIRECTION_CALL) {
                    elevator.addDestinationFloor(request.getFromSrcFloor());
                } else { // else, if destination-floor selection
                    elevator.addDestinationFloor(request.getToDestFloor());
                }

                request.setRequestStatus(RequestStatus.ASSIGNED);

                UserRequestCache.getActiveRequests().put(request.getRequestId(), request);
                UserRequestCache.getPendingRequests().remove(request);

                // set elevator state if it was idle
                if (elevator.isStandingIdle()) {
                    int cur = elevator.getCurrentFloor() == null ? 1 : elevator.getCurrentFloor().get();
                    ElevatorState newState = request.getToDestFloor() > cur ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN;
                    elevator.setElevatorState(newState);
                }

                // persist change
                this.elevatorRepository.save(elevator);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                UserRequestCache.getPendingRequests().offer(request);
            } finally {
                if (locked) {
                    elevator.getLock().unlock();
                }
            }
    }



    private Elevator processPendingFloorRequest(ElevatorRequest request) {
        // Scheduler determines which floor request is suitable to be mapped to which elevator
        Elevator bestElevator = scheduler.findBestElevator(ElevatorCache.elevators, request);

        if (bestElevator != null) {
            assignRequestToElevator(request, bestElevator);
        } else { // if scheduler cannot find suitable/optimal elevator to serve that particular floor req
            // Re-queue if no elevator available
            UserRequestCache.getPendingRequests().offer(request);
        }
        return bestElevator;
    }

    public void processAllPendingFloorRequests() {
        // Continuously pull floor request from waiting queue until it gets empty
        while (!UserRequestCache.getPendingRequests().isEmpty()) {
            Elevator assignedElevator = this.processPendingFloorRequest(UserRequestCache.getPendingRequests().poll());
            if(Objects.isNull(assignedElevator))
                break;
        }
    }

    // In case of elevator specific Floor Direction Call,
    // you already know the elevator which you want to use
    // then you request that particular elevator
    public Elevator requestParticularElevator(Elevator chosenElevator, RequestDirection requestDirection, int requestedFromFloor, RequestPriority requestPriority) {
        ElevatorRequest request = new ElevatorRequest(requestPriority, requestedFromFloor, requestDirection);
        this.assignRequestToElevator(request, chosenElevator); // chosen elevator will first reach src floor
        return chosenElevator;
    }

    // In case of global Floor Direction Call,
    // algo needs to decide which elevator to map to
    public Elevator requestElevator(RequestDirection requestDirection, int requestedFromFloor, RequestPriority requestPriority) {
        ElevatorRequest request = new ElevatorRequest(requestPriority, requestedFromFloor, requestDirection);
        Elevator bestElevator = this.processPendingFloorRequest(request);
        return bestElevator;
    }

    // if elevator is already chosen (e.g., in case of in-elevator destination floor selection)
    public void selectDestinationFloorInsideRequestedElevator(Elevator chosenElevator, int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        ElevatorRequest request = new ElevatorRequest(requestPriority, requestedFromFloor, toDestFloor);
        UserRequestCache.getPendingRequests().offer(request);
        this.assignRequestToElevator(request, chosenElevator); // chosen elevator will then reach src floor
    }

    // 3. In case of global Destination Floor Selection, algo needs to decide which elevator to map to
    public void selectDestinationFloor(int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        ElevatorRequest request = new ElevatorRequest(requestPriority, requestedFromFloor, toDestFloor);
        UserRequestCache.getPendingRequests().offer(request);
        this.processPendingFloorRequest(request);
    }
}
