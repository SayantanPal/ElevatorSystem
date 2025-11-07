package com.example.demo.scheduler;

import com.example.demo.enums.ElevatorState;
import com.example.demo.enums.RequestType;
import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;
import com.example.demo.utility.Helper;

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
                && elevator.getCurrentFloor().get() <= request.getFromSrcFloor())

                // OR
                || ((elevator.isMovingDown() && request.isDownward())
                && elevator.getCurrentFloor().get() >= request.getFromSrcFloor());
    }

    @Override
    public Elevator findBestElevator(List<Elevator> elevators, ElevatorRequest request){
        List<Elevator> suitableElevators = elevators.stream()
                                                    .filter(elevator -> {
                                                        int targetFloor = (request.getRequestType() == RequestType.FLOOR_DIRECTION_CALL)
                                                                ? request.getFromSrcFloor()
                                                                : request.getToDestFloor();
//                                                        return elevator.canAcceptFloorServeRequest(request.getToDestFloor());
                                                        return elevator.canAcceptFloorServeRequest(targetFloor);
                                                    })
                                                    .filter(elevator -> this.isElevatorSuitable(elevator, request))
                                                    .toList();

        if (suitableElevators.isEmpty()) {
            return null;
        }

        // find nearest elevator
        Elevator nearestElevator = suitableElevators.stream()
                                .min(Comparator.comparingInt(elevator -> Math.abs(elevator.getCurrentFloor().get() - request.getFromSrcFloor())))
                                .orElse(suitableElevators.get(0));

        return nearestElevator;

    }

    public Map<ElevatorRequest, Elevator> findBestElevator(List<Elevator> elevators, List<ElevatorRequest> requests){
        Map<ElevatorRequest, Elevator> bestElevator = new HashMap<>();
        requests.forEach(req -> bestElevator.put(req, this.findBestElevator(elevators, req)));
        return bestElevator;
    }
}
