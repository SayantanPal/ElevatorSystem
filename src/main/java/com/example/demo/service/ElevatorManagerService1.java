package com.example.demo.service;

import com.example.demo.IConstants;
import com.example.demo.cache.ElevatorCache;
import com.example.demo.enums.ElevatorState;
import com.example.demo.model.Elevator;
import com.example.demo.repository.ElevatorRepository;
import com.example.demo.scheduler.SCANScheduler;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ElevatorManagerService1 implements Serializable {

    private final ElevatorRepository elevatorRepository;
    private final ElevatorMovementService1 elevatorMovementService;
    private final ScheduledExecutorService pendingRequestRetryExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ElevatorDispatcherService elevatorDispatcherService;

    public ElevatorManagerService1(){
        this.elevatorRepository = new ElevatorRepository();
        this.elevatorMovementService = ElevatorMovementService1.getInstance();
        this.elevatorDispatcherService = ElevatorDispatcherService.getInstance();
        this.initElevator();
    }


    public void initElevator(){
        this.createElevator(IConstants.INITIAL_ELEVATOR_COUNT);
        pendingRequestRetryExecutor.scheduleWithFixedDelay( // we never want the background processing job to run again immediately
                this.elevatorDispatcherService::processPendingRequestsSafely,
                1, // initial delay in seconds
                1,          // cooldown after finish: retry every 1 second
                TimeUnit.SECONDS
        );
    }

    /*
     * createElevator() must add the elevator to repository/cache
     * and then inform ElevatorMovementService to schedule movement for the newly created elevator
     *
     * Always scheduled per elevator (constructor approach):
     * Pros:
     *   Movement thread exists from day 1 → no latency when a request is assigned.
     *   Scheduler doesn’t have to check if the thread exists → simpler code.
     *   Continuous monitoring → can easily implement “pickup requests on the way” without extra scheduling logic.
     *
     * Cons:
     * Even idle elevators consume 1 scheduled task slot per second, small CPU/memory overhead.
     * In very large systems (hundreds of elevators), many idle threads may be “wasting” cycles.
     * */
    public Elevator createElevator(){
        // 1. Create Elevator in IDLE state
        Elevator e = new Elevator(ElevatorState.IDLE);

        // 2. Add to cache & repository
        ElevatorCache.elevators.add(e);
        this.elevatorRepository.save(e);

        // 3. Register/Schedule the movement thread immediately after creating the elevator.
        // This ensures every elevator has a scheduled movement thread from the moment it exists
        this.elevatorMovementService.startElevator(e);
        return e;
    }

    public List<Elevator> createElevator(int count){
        List<Elevator> created = new ArrayList<>();
        for(int i = 0; i < count; i++){
            created.add(this.createElevator());
        }
        return created;
    }

    private static class ElevatorManagerServiceHolder {
        @Serial
        private static final long serialVersionUID = 1L;
        private static final ElevatorManagerService1 INSTANCE = new ElevatorManagerService1();
    }

    public static ElevatorManagerService1 getInstance() {
        return ElevatorManagerService1.ElevatorManagerServiceHolder.INSTANCE;
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
