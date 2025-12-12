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

    // Returns a small integer representing direction desirability.
    // 0: aligned with request direction, 1: idle, 2: opposite.
    private int directionPriority(Elevator e, ElevatorRequest r) {
        boolean upReq = r.isUpward();
        boolean downReq = r.isDownward();

        // 1st choice - same direction alignment
        if (upReq && e.isMovingUp()) return 0;
        if (downReq && e.isMovingDown()) return 0;

        // 2nd choice - idle
        if (e.isStandingIdle()) return 1;

        // 3rd and last choice - opposite direction
        return 2;
    }

    /**
     * Compute an ordered list of candidate elevators for the given request.
     * This method is intentionally read-only and lock-free; the dispatcher
     * MUST re-validate suitability after lock acquisition due to potential
     * state changes (TOCTOU).
     *
     * Scoring model (sorted ascending — lower is better):
     * - abs(distance to pickup src floor)
     * - directionPriority (aligned → idle → opposite)
     * - current load (assignedFloors size)
     * - tie-breaker jitter to reduce herd effects when scores are equal
     *
     * Eligibility:
     * - Elevator must be able to accept the request (e.g., not in maintenance/emergency).
     * - Request must be valid.
     *
     * Fallback policy:
     * - Opposite-direction elevators are NOT filtered out; they appear later in the list
     *   to provide deterministic fallback when all aligned/idle options are unavailable.
     *
     * @param elevators all current elevators in the system
     * @param request   the floor/destination request to evaluate
     * @return ordered list of eligible elevators, possibly empty
     */
    @Override
    public List<Elevator> findBestElevators(List<Elevator> elevators, ElevatorRequest request) {
        // find working elevator and also filter legitimate/valid requests
        // filter elevator not in any emergency or other state preventing it from serving valid requests
        List<Elevator> eligible = elevators.stream()
            .filter(e -> e.canAcceptFloorServeRequest(request.getFromSrcFloor()))
            .filter(e -> Validator.isValidRequest(request))
            .toList();

        // if all elevators are totally restricted(maintainenece/emergency) to serve that floor request
        if (eligible.isEmpty()) {
            return List.of();
        }

        // when the elevators in working state and elevator request is valid/legitimate
        Comparator<Elevator> cmp =
            Comparator.comparingInt((Elevator e) -> Math.abs(e.getCurrentFloor() - request.getFromSrcFloor()))
                      .thenComparingInt(e -> directionPriority(e, request))
                      .thenComparingInt(Elevator::getNoOfIncomingFloorServeRequest)
                      // small jitter to avoid stampede; affects only near-equal cases
                      .thenComparingDouble(e -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());

        return eligible.stream()
            .sorted(cmp)
            .toList();
    }

    // Returns Null when no elevator working or request is invalid
    @Override
    public Elevator findBestElevator(List<Elevator> elevators, ElevatorRequest request){
        List<Elevator> ordered = findBestElevators(elevators, request);
        return ordered.isEmpty() ? null : ordered.getFirst();
    }

    public Map<ElevatorRequest, Elevator> findBestElevator(List<Elevator> elevators, List<ElevatorRequest> requests){
        Map<ElevatorRequest, Elevator> bestElevator = new HashMap<>();
        requests.forEach(req -> bestElevator.put(req, this.findBestElevator(elevators, req)));
        return bestElevator;
    }
}
