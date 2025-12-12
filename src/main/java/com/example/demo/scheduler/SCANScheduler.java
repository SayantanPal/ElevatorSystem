package com.example.demo.scheduler;

import com.example.demo.enums.ElevatorState;
import com.example.demo.enums.RequestType;
import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;
import com.example.demo.utility.Helper;
import com.example.demo.utility.Validator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
* The SCAN algorithm is much more efficient for high-rise buildings
* because it minimizes the number of direction changes.
* It’s like how elevators in real buildings work — they pick up people going in the same direction
* */


public class SCANScheduler implements ElevatorScheduler {

    // SCAN algorithm: prefer elevators moving in the same direction
    private boolean isElevatorSuitable(Elevator elevator, ElevatorRequest request) {

        return (elevator.isStandingIdle())

                // Elevator is moving in same direction as per floor request(either up or down) and
                // elevator has not yet passed/crossed the floor from where it has been requested

                // OR
                || ((elevator.isMovingUp() && request.isUpward())
                && elevator.getCurrentFloor() <= request.getFromSrcFloor())

                // OR
                || ((elevator.isMovingDown() && request.isDownward())
                && elevator.getCurrentFloor() >= request.getFromSrcFloor());
    }

    // Returns Null when no elevator working or request is invalid
    @Override
    public Elevator findBestElevator(List<Elevator> elevators, ElevatorRequest request){
        // find eligible elevators which can either stay idle or moving in same direction as per request
        //find working elevator and also filter legitimate/valid requests
        List<Elevator> eligibleElevators = elevators.stream()
                                                    .filter(elevator -> {
//                                                        int targetFloor = (request.getRequestType() == RequestType.FLOOR_DIRECTION_CALL)
//                                                                ? request.getFromSrcFloor()
//                                                                : request.getToDestFloor();
                                                        int targetToBePickedFromSrcFloor = request.getFromSrcFloor();
//                                                        return elevator.canAcceptFloorServeRequest(request.getToDestFloor());
                                                        return elevator.canAcceptFloorServeRequest(targetToBePickedFromSrcFloor)
                                                                && Validator.isValidRequest(request);
                                                    }) // filter elevator not in any emergency or other state preventing it from serving valid requests
                                            .toList();

        if(eligibleElevators.isEmpty()){
            // if all elevators are totally restricted(maintainenece/emergency) to serve that floor request
            return null;
        }

        // when the request is valid/legitimate and at least one elevator in working state to serve the request
        Elevator nearestSuitableElevator  = eligibleElevators.stream().filter(elevator -> this.isElevatorSuitable(elevator, request))// elevator moving in same dir or idle
                                                    .min(Comparator.comparingInt((Elevator elevator) -> Math.abs(elevator.getCurrentFloor() - request.getFromSrcFloor())) // get min dist elevator
                                                    .thenComparing(elevator -> elevator.getElevatorState() == ElevatorState.IDLE ? 1 : 0)) // prioritize moving elevators over idle ones if min distance is same as that of same dir moving elevator
                                                    .orElse(eligibleElevators.get(0)); // fallback(when all working elevators are moving in opposite direction) to first elevator as standby elevator by default if no elevator moving in same dir or any idle elevator found

        return nearestSuitableElevator;
    }

    public Map<ElevatorRequest, Elevator> findBestElevator(List<Elevator> elevators, List<ElevatorRequest> requests){
        Map<ElevatorRequest, Elevator> bestElevator = new HashMap<>();
        requests.forEach(req -> bestElevator.put(req, this.findBestElevator(elevators, req)));
        return bestElevator;
    }
}
