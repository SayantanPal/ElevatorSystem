package com.example.demo.service;

import com.example.demo.ElevatorController;
import com.example.demo.cache.ElevatorCache;
import com.example.demo.cache.UserRequestCache;
import com.example.demo.enums.ElevatorState;
import com.example.demo.enums.RequestDirection;
import com.example.demo.enums.RequestStatus;
import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;
import com.example.demo.repository.ElevatorRepository;
import com.example.demo.utility.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
* Core Idea of Async Non-Blocking Execution

    Non-blocking means: The main thread (or elevator control loop) does not pause or sleep waiting for time delays like door open/close — it simply schedules those actions to happen later, while continuing with other work.
    Async means: Those delayed actions are run on separate worker threads, managed by an executor
    *
    * scheduleAtFixedRate() => next run start time = previous start time + period
    * scheduleAtFixedRate keeps strictly fixed intervals (say, 5s apart), regardless of how long the task took (as long as it finishes before next period).
    * if your task takes longer than the period, next run will start immediately after previous completes, no overlap — but it’ll skip missed periods to catch up.
    * ideal for Elevator system Periodic monitoring of dispatcher loop (checking queue every 500ms, etc)
    *
    * scheduleWithFixedDelay() => The next run start time = previous end time + delay
    * scheduleWithFixedDelay has fixed “cooldown” period after each task completes.
    * scheduleWithFixedDelay waits for the previous execution to complete, and only after that waits another delay before starting the next one.
* */

public class ElevatorMovementService implements Serializable {

    private static final long serialVersionUID = 1L;

    // Instance-level mutable state - state change is involved - hence non-static below
    private final ElevatorRepository elevatorRepository;
    private final ScheduledExecutorService movementExecutor;

    // Optional: track scheduled tasks if you want to cancel later
    private final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    // Static utility components (shared, not business state) - Read only dependency - No state change
    private static final Logger LOGGER = LoggerFactory.getLogger(ElevatorMovementService.class);


    private ElevatorMovementService() {
        this.elevatorRepository = new ElevatorRepository();
        int noOfElevators = this.elevatorRepository.findCountOfElevators();
        // used to run the given tasks periodically or once after a given certain delay without blocking the caller.
        this.movementExecutor = Executors.newScheduledThreadPool(noOfElevators); // One thread per elevator

        // Start movement processing for each elevator
        startMovementProcessingForExisting(); // startMovementProcessing();
    }

    /*
    * Only one executor runs across the system.
    * All elevators share the same scheduler.
    * Thread-safe, lazy initialization.
    * */

    // Static inner class responsible for holding the instance
    private static class Holder {
        private static final ElevatorMovementService INSTANCE = new ElevatorMovementService();
    }

    // Global access point
    public static ElevatorMovementService getInstance() {
        return Holder.INSTANCE;
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

    private void startMovementProcessingForExisting() {
        List<Elevator> elevators = ElevatorCache.elevators; // shared list
        for (Elevator elevator : elevators) {
            scheduleElevator(elevator);
        }
    }

    // call this from manager after creating a new elevator
    public void registerElevator(Elevator elevator) {
        scheduleElevator(elevator);
    }

    /**
     * Submits a periodic action that becomes enabled first after the given initial delay, and subsequently with the given period;
     * That is, executions will commence in AP series like: after initialDelay, then initialDelay + period, then initialDelay + 2 * period, and so on.
     */

    private void scheduleElevator(Elevator elevator) {
        if (scheduledTasks.containsKey(elevator.getElevatorId())) return;
        ScheduledFuture<?> future = movementExecutor.scheduleAtFixedRate( // Non-Blocking Async Behavior - No Thread.sleep() or waiting involved - The call returns immediately.
                () -> moveElevator(elevator.getElevatorId()),
                0, 1, TimeUnit.SECONDS // The action runs 1 seconds later on a different thread.
        );
        scheduledTasks.put(elevator.getElevatorId(), future);
    }

    /*
    private void startMovementProcessing() {
        List<Elevator> elevators = elevatorRepository.findAll();
        for (Elevator elevator : elevators) {
            // Submits a periodic action that becomes enabled first after the given initial delay, and subsequently with the given period;
            // that is, executions will commence in AP series like: after initialDelay, then initialDelay + period, then initialDelay + 2 * period, and so on.
            movementExecutor.scheduleAtFixedRate(
                    () -> moveElevator(elevator.getElevatorId()), // runnable task
                    0, 1, TimeUnit.SECONDS // Elevators Move every second
            );
        }
    }
    */

    private void processFloorRequests(Elevator elevator, int floor) {
        List<ElevatorRequest> floorRequests = Helper.getActiveRequestsFromFloor(floor);
        for (ElevatorRequest request : floorRequests) {
            if (Helper.checkElevatorMovingInSameDirectionAsFloorReq(elevator, request)
                    || elevator.isStandingIdle()) {
                elevator.addDestinationFloor(request.getToDestFloor());
                request.setRequestStatus(RequestStatus.IN_PROGRESS);
                Helper.makePendingRequestActiveForServing(request);
            }
        }
    }

    private void handleArrival(Elevator elevator) {
        int currentFloor = elevator.getCurrentFloor().get();

        // Remove this floor from assigned destinations list for serving elevator
        elevator.removeDestinationFloor(currentFloor);

        // Process any requests from this floor
        processFloorRequests(elevator, currentFloor);

        // Determine next direction or go idle
        if (elevator.getDestinationFloors().isEmpty()) {
            elevator.setElevatorState(ElevatorState.IDLE);
            ElevatorManagerService.getInstance().processAllPendingFloorRequests();
        } else {
            int nextFloor = elevator.findNearestImmediateFloor();
            ElevatorState newState = nextFloor > currentFloor ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN;
            elevator.setElevatorState(newState);
        }
    }

    private boolean shouldStopAtFloor(Elevator elevator, int floor) {
        // Stop if this floor is a destination
        if (elevator.getDestinationFloors().contains(floor)) {
            return true;
        }

        // Stop if there's a pending request from this floor going in same direction
        Queue<ElevatorRequest> pendingFloorServeRequests = UserRequestCache.getPendingRequests();
        return pendingFloorServeRequests.stream()
                .anyMatch(request ->
                        request.getFromSrcFloor() == floor // pending/unassigned requested floor matches with currently moving floor
                        && (Helper.checkElevatorMovingInSameDirectionAsFloorReq(elevator, request))
                        && request.getRequestStatus() == RequestStatus.PENDING);
    }

    private void moveOneFloor(Elevator elevator, int targetFloor) {
        int currentFloor = elevator.getCurrentFloor().get();
        int nextFloor = currentFloor + (targetFloor > currentFloor ? 1 : -1);

        // Update elevator position
        // next floor becomes current floor now
        elevator.setCurrentFloor(new AtomicInteger(nextFloor));

        // Check if we should stop at this current floor
        if (shouldStopAtFloor(elevator, nextFloor)) {
            stopAtFloor(elevator, nextFloor);
        }
    }

    public void moveElevator(String elevatorId) {
        Elevator elevator = elevatorRepository.findById(elevatorId);
        if (elevator == null || !elevator.isMoving()) {
            return;
        }

        int nextFloor = elevator.findNearestImmediateFloor();
        if (nextFloor == elevator.getCurrentFloor().get()) { // if it has arrived at destination
            handleArrival(elevator);
        } else { // if it has not yet arrived at destination floor
            // Move one floor
            moveOneFloor(elevator, nextFloor);
        }
    }

    /* it becomes generic, reusable door-handling utility
    * “what to do after doors close” is customizable here
    * The method focuses on one responsibility — door operations only.
    * */
    private void simulateDoorOperations(Elevator elevator, Runnable afterDoorCloseAction) {
        System.out.printf("[Elevator %s] Doors opening at floor %d%n",
                elevator.getElevatorId(), elevator.getCurrentFloor().get());

        // Door open + loading + door close simulation (non-blocking)
        elevator.setElevatorState(ElevatorState.LOADING);
        movementExecutor.schedule(() -> {
            System.out.printf("[Elevator %s] Door opened in 1 sec. Loading passengers...%n", elevator.getElevatorId());
        }, 1, TimeUnit.SECONDS); // door open time

        movementExecutor.schedule(() -> {
            System.out.printf("[Elevator %s] Passenger Loaded in 10 sec. Doors closing in 1 sec...%n", elevator.getElevatorId());
            elevator.setElevatorState(ElevatorState.IDLE);
            if (afterDoorCloseAction != null) {
                afterDoorCloseAction.run();
            }
        }, 11, TimeUnit.SECONDS); // total: ~10 sec load + 1 sec close
    }

    private void stopAtFloor(Elevator elevator, int floor) {
        // Remove from destinations
        elevator.removeDestinationFloor(floor);

        // Process requests originating at this floor (assign elevator destinations and update statuses)
        List<ElevatorRequest> floorRequests = Helper.getActiveRequestsFromFloor(floor);
        for (ElevatorRequest request : floorRequests) {
            if (request.getRequestStatus() == RequestStatus.PENDING
                    && Helper.checkElevatorMovingInSameDirectionAsFloorReq(elevator, request)) {
                // assign this elevator to the request: mark in-progress and add destination
                request.setRequestStatus(RequestStatus.IN_PROGRESS);
                elevator.addDestinationFloor(request.getToDestFloor());
                Helper.makePendingRequestActiveForServing(request);
            }
        }

        // If any in-elevator requests target this floor, mark them completed
        // Inherently Provides Strong temporal consistency — Gives effectively that particular point-in-time/moment view of the queue ie “data as of moment T” (working on a stale copy than live data but ensuring consistent view of the data) although the original queue may be changing concurrently by other threads
        // Immutable Snapshot Copy gives you a consistent view of the queue at a single point in time without locking/blocking other threads - maintains concurrency discipline
        // You get deterministic content (snapshot) + You still process in parallel (performance boost)
        // changes to the original queue during processing by multiple threads do not affect this snapshot
        List<ElevatorRequest> localSnapshotOfPendingRequests = new ArrayList<>(UserRequestCache.getPendingRequests());
        localSnapshotOfPendingRequests.parallelStream()
                    .filter(r -> r.getToDestFloor() == floor && r.getRequestStatus() == RequestStatus.IN_PROGRESS)
                    .forEach(r -> {
                        r.setRequestStatus(RequestStatus.COMPLETED);
                });

        // Note: do not block thread here (no sleeping). Door open/close timings should be handled elsewhere if needed.
        // Simulate doors open/close & continue movement afterwards

        simulateDoorOperations(elevator, () -> {
            // if elevator has still some assigned active destination floor requests ready to be served
            if (!elevator.getDestinationFloors().isEmpty()) {
                int nextFloor = elevator.findNearestImmediateFloor();
                ElevatorState newState = nextFloor > floor ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN;
                elevator.setElevatorState(newState);
                System.out.printf("[Elevator %s] Resuming movement towards floor %d%n",
                        elevator.getElevatorId(), nextFloor);
            } else { // if elevator has already served all the active floor requests
                elevator.setElevatorState(ElevatorState.IDLE);
                System.out.printf("[Elevator %s] Now idle at floor %d%n",
                        elevator.getElevatorId(), floor);
            }
        });
    }


}
