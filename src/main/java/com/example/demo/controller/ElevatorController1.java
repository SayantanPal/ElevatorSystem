package com.example.demo.controller;


import com.example.demo.enums.RequestDirection;
import com.example.demo.enums.RequestPriority;
import com.example.demo.model.Elevator;
import com.example.demo.service.ElevatorDispatcherService;
import com.example.demo.service.ElevatorManagerService;
import com.example.demo.service.ElevatorManagerService1;

import java.io.Serializable;
import java.util.List;

public class ElevatorController1 {

    private final ElevatorDispatcherService elevatorDispatcherService;
    private final ElevatorManagerService1 elevatorManagerService1;

    public ElevatorController1() {
        this.elevatorDispatcherService = ElevatorDispatcherService.getInstance();//new ElevatorManagerService1();
        this.elevatorManagerService1 = ElevatorManagerService1.getInstance();
    }

    public Elevator createElevator() {
        return this.elevatorManagerService1.createElevator();
    }

    public List<Elevator> createElevator(int count) {
        return this.elevatorManagerService1.createElevator(count);
    }


    // 3. In case of global Destination Floor Selection, algo needs to decide which elevator to map to
    public void selectDestinationFloorOutsideElevator(int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        this.elevatorDispatcherService.selectDestinationFloorOutsideForBothPickUpAndDropOff(requestedFromFloor, toDestFloor, requestPriority);
    }

}
