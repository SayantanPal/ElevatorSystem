package com.example.demo.scheduler;

import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;

import java.util.List;
import java.util.Map;

public interface ElevatorScheduler {
    public Elevator findBestElevator(List<Elevator> elevators, ElevatorRequest request);

    /**
     * Returns an ordered list of eligible elevators for the given request.
     * The list is sorted by desirability and should be used by dispatchers
     * to attempt locking in order (top-K strategy) while re-validating state
     * after acquiring each per-elevator lock (closes the TOCTOU gap).
     *
     * Contract:
     * - Pure read-only: no side effects or mutation of elevators/requests.
     * - Eligibility is based on the elevator’s ability to accept the request
     *   (e.g., not in maintenance/emergency) and request validity.
     * - Ordering generally favors:
     *   1) shorter distance to pickup floor,
     *   2) direction-aligned elevators over idle over opposite,
     *   3) lower current load (fewer assigned floors),
     *   4) a tiny jitter tie-breaker to avoid stampede on identical scores.
     *
     * Caller responsibilities:
     * - Re-validate suitability AFTER acquiring the per-elevator lock, since state may
     *   change between selection and mutation in a concurrent system.
     *
     * @param elevators all current elevators in the system
     * @param request   the floor/destination request to evaluate
     * @return ordered list of eligible elevators, possibly empty if none can serve
     */
    public List<Elevator> findBestElevators(List<Elevator> elevators, ElevatorRequest request);

    public Map<ElevatorRequest, Elevator> findBestElevator(List<Elevator> elevators, List<ElevatorRequest> requests);
}
