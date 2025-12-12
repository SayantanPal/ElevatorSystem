package com.example.demo.model;
import com.example.demo.IConstants;
import com.example.demo.enums.ElevatorState;
import com.example.demo.utility.Helper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/*
* Option - Approach using ArrayList for destination floor leads to multiple inefficiencies:
* -------------------------------------------------------------------------------
  contains() → O(n)
  sort() after every addition → O(n log n)
  remove() → O(n)
  Finding next higher/lower floor → O(n) with stream().filter()
*
* Option - PriorityBlockingQueue:
* -------------------------------
    - PriorityBlockingQueue for thread-safe use is a min-heap. It can efficiently get the smallest (or largest) element → O(1). Insertions/removals are O(log n)
    - But… it doesn’t maintain sorted order for arbitrary traversal.
    - You can’t get "next higher" or "next lower" — you only get the top priority element.

* Option - Improved Approach(using TreeSet + ReadWriteLock or ConcurrentSkipListSet) :
* ---------------------
* All major operations (add, remove, contains, higher, lower) in ConcurrentSkipListSet are still O(log n) — similar to TreeSet.
* While TreeSet is slightly faster in single-threaded operations and is not thread-safe,
* TreeSet scales poorly in concurrent workloads and if two threads call add() or remove() at the same time, the internal Red-Black tree structure can get corrupted — causing exceptions or incorrect results
* The difference is that concurrency overhead slightly increases constants but massively improves throughput under load.
*
* Advantages of Thread-safe ConcurrentSkipListSet:
* ------------------------------------
* Lock-free reads: Multiple threads can read concurrently without blocking.
* Fine-grained concurrency: Different threads can update disjoint parts of the set simultaneously.
* Safe iteration: The iterator reflects a “snapshot” view and doesn’t throw ConcurrentModificationException.
* Still sorted: Maintains sorted order at all times, just like TreeSet.
* Fully thread-safe: No external synchronization needed.
* */


public class Elevator {
    @Getter
    private final String elevatorId;

    @Getter
    private final ConcurrentSkipListSet<Integer> assignedFloors;

    // Per-elevator lock used for fine-grained concurrency
    private final ReentrantLock lock = new ReentrantLock(true);

    private volatile AtomicInteger currentFloor;

    private volatile AtomicReference<ElevatorState> elevatorState;


    public Elevator(ElevatorState elevatorState){
        this(elevatorState, IConstants.BASE_FLOOR); // start at ground floor by default
    }

    public Elevator(ElevatorState elevatorState, int startingFloor){
        this.elevatorId = Helper.generateUUID();
        this.elevatorState = new AtomicReference<>(elevatorState);
        this.assignedFloors = new ConcurrentSkipListSet<>();
        this.currentFloor = new AtomicInteger(startingFloor);
    }

    public boolean isMoving(){
        return this.isMovingUp() || this.isMovingDown();
    }

    public boolean isMovingUp(){
        return (this.elevatorState.get() == ElevatorState.MOVING_UP);
    }

    public boolean isMovingDown(){
        return (this.elevatorState.get() == ElevatorState.MOVING_DOWN);
    }

    public boolean isStandingIdle(){
        return (this.elevatorState.get() == ElevatorState.IDLE);
    }

    public int getNoOfIncomingFloorServeRequest(){
        return this.assignedFloors.size();
    }

    public boolean canAcceptFloorServeRequest(int floor){
        return !((this.elevatorState.get() == ElevatorState.MAINTENANCE)
                || (this.elevatorState.get() == ElevatorState.EMERGENCY));
//                && (this.getNoOfIncomingFloorServeRequest() <= IConstants.MAX_HOLDING_CAPACITY)
//                && (floor >= IConstants.BASE_FLOOR && floor <= IConstants.MAX_FLOOR_COUNT);
    }

    public void addFloor(int destFloor){
        if (destFloor >= IConstants.BASE_FLOOR && destFloor <= IConstants.MAX_FLOOR_COUNT)
            this.assignedFloors.add(destFloor); // T(n) = O(logn)
    }

    public void addFloor(Collection<Integer> destFloors){
        this.assignedFloors.addAll(destFloors);
    }

    public void removeFloor(int destFloor){
        this.assignedFloors.remove(destFloor); // T(n) = O(logn)
    }

    public void removeFloor(Collection<Integer> destFloors){
        this.assignedFloors.removeAll(destFloors);
    }

    /*
     * Elevator uses below Logic to determine among its assigned floor requests, which active floor request to serve first
     */
    public int findNearestImmediateFloor(){
        if(assignedFloors.isEmpty()) return currentFloor.get();

        Integer nearestFloor = null;

        if(this.isMovingUp()){
            Integer nearestUpFloor = this.assignedFloors.higher(this.currentFloor.get()); // immediate higher
            nearestFloor = (nearestUpFloor != null) ? nearestUpFloor : this.assignedFloors.lower(this.currentFloor.get());
        }else if(this.isMovingDown()){
            Integer nearestDownFloor = this.assignedFloors.lower(this.currentFloor.get()); // immediate lower
            nearestFloor = (nearestDownFloor != null) ? nearestDownFloor : this.assignedFloors.higher(this.currentFloor.get());
        } else {
            // if idle, choose the closest in absolute terms
            nearestFloor = this.assignedFloors.stream()
                    .min(Integer::compareTo)
                    .orElse(this.currentFloor.get());
        }

        if(nearestFloor == null){
            return assignedFloors.last();
        }
        return nearestFloor;
    }

    public void setElevatorState(ElevatorState elevatorState){
        this.elevatorState.set(elevatorState);
    }

    public ElevatorState getElevatorState() {
        return elevatorState.get();
    }

    public void setCurrentFloor(int floor){
        this.currentFloor.set(floor);
    }

    public int getCurrentFloor() {
        return currentFloor.get();
    }

    // Getter used by manager/service code
    public ReentrantLock getLock() {
        return lock;
    }

    @Override
    public String toString() {
        return "Elevator{" +
                "elevatorId='" + elevatorId + '\'' +
                ", assignedFloors=" + assignedFloors +
                ", lock=" + lock +
                ", currentFloor=" + currentFloor +
                ", elevatorState=" + elevatorState +
                '}';
    }
}
