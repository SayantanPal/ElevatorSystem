package com.example.demo.scheduler;

import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;

import java.util.List;
import java.util.Map;

public interface ElevatorScheduler {
    public Elevator findBestElevator(List<Elevator> elevators, ElevatorRequest request);
    public Map<ElevatorRequest, Elevator> findBestElevator(List<Elevator> elevators, List<ElevatorRequest> requests);
}