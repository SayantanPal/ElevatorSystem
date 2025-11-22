package com.example.demo;


import com.example.demo.enums.RequestDirection;
import com.example.demo.enums.RequestPriority;
import com.example.demo.model.Elevator;
import com.example.demo.service.ElevatorManagerService;

public class ElevatorController {

    private final ElevatorManagerService elevatorManagerService;

    public ElevatorController() {
        this.elevatorManagerService = ElevatorManagerService.getInstance();
    }

    public Elevator createElevator() {
        return this.elevatorManagerService.createElevator();
    }

    // In case of elevator specific Floor Direction Call,
    // you already know the elevator which you want to use
    // then you request that particular elevator
    public Elevator requestParticularElevator(Elevator chosenElevator, RequestDirection requestDirection, int requestedFromFloor, RequestPriority requestPriority) {
        return this.elevatorManagerService.requestParticularElevator(chosenElevator, requestDirection, requestedFromFloor, requestPriority);
    }

    // In case of global Floor Direction Call,
    // algo needs to decide which elevator to map to
    public Elevator requestElevator(RequestDirection requestDirection, int requestedFromFloor, RequestPriority requestPriority) {
        return this.elevatorManagerService.requestElevator(requestDirection, requestedFromFloor, requestPriority);
    }

    // if elevator is already chosen (e.g., in case of in-elevator destination floor selection)
    public void selectDestinationFloorInsideRequestedElevator(Elevator chosenElevator, int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        this.elevatorManagerService.selectDestinationFloorInsideRequestedElevator(chosenElevator, requestedFromFloor, toDestFloor, requestPriority);
    }


    // 3. In case of global Destination Floor Selection, algo needs to decide which elevator to map to
    public void selectDestinationFloor(int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        this.elevatorManagerService.selectDestinationFloor(requestedFromFloor, toDestFloor, requestPriority);
    }
}
