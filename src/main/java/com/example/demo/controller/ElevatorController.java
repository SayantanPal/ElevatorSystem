package com.example.demo.controller;


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


    // SCENARIO - 1: Elevator Specific FLOOR DIRECTION CALL + Followup Destination Floor Selection INSIDE ELEVATOR
        // --> requestSelfChosenElevator()[requestParticularElevatorForPickUp()/FLOOR DIRECTION] + selectDestinationFloorInsideRequestedElevator()[DESTINATION SELECTION]

    // SCENARIO - 2: Global FLOOR DIRECTION CALL + Followup Destination Floor Selection INSIDE ELEVATOR
        // --> requestElevatorFromScheduler()[requestElevatorForPickUp()/FLOOR DIRECTION] + selectDestinationFloorInsideRequestedElevator()[DESTINATION SELECTION]

    // SCENARIO - 3: Global DESTINATION FLOOR SELECTION OUTSIDE
        // --> selectDestinationFloorOutsideElevator()[selectDestinationFloorOutsideForBothPickUpAndDropOff()/DESTINATION SELECTION]


    // In case of elevator specific Floor Direction Call,
    // you already know the elevator which you want to use
    // then you request that particular elevator
    public Elevator requestSelfChosenElevator(Elevator chosenElevator, RequestDirection requestDirection, int requestedFromFloor, RequestPriority requestPriority) {
        return this.elevatorManagerService.requestParticularElevatorForPickUp(chosenElevator, requestDirection, requestedFromFloor, requestPriority);
    }

    // In case of global Floor Direction Call,
    // algo needs to decide which elevator to map to
    public Elevator requestElevatorFromScheduler(RequestDirection requestDirection, int requestedFromFloor, RequestPriority requestPriority) {
        return this.elevatorManagerService.requestElevatorForPickUp(requestDirection, requestedFromFloor, requestPriority);
    }

    // if elevator is already chosen (e.g., in case of in-elevator destination floor selection)
    public void selectDestinationFloorInsideRequestedElevator(Elevator chosenElevator, int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        this.elevatorManagerService.selectDestinationFloorInsideRequestedElevatorForDropOff(chosenElevator, requestedFromFloor, toDestFloor, requestPriority);
    }


    // 3. In case of global Destination Floor Selection, algo needs to decide which elevator to map to
    public void selectDestinationFloorOutsideElevator(int requestedFromFloor, int toDestFloor, RequestPriority requestPriority) {
        this.elevatorManagerService.selectDestinationFloorOutsideForBothPickUpAndDropOff(requestedFromFloor, toDestFloor, requestPriority);
    }
}
