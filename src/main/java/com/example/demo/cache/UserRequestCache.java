package com.example.demo.cache;

import com.example.demo.model.ElevatorRequest;
import lombok.Getter;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UserRequestCache {

    // Floor Requests which are yet to be assigned/mapped to any elevator
    // waits in a queue called Pending Floor Req
    // ConcurrentLinkedQueue is safe but not strongly consistent
    // because though they don’t throw ConcurrentModificationException — that’s good, but they don’t lock the queue either
    // which makes it Perfect for monitoring or logging, but not for deterministic decision-making (like scheduling next elevator stop).
    //
    @Getter
    private static final Queue<ElevatorRequest> pendingRequests = new ConcurrentLinkedQueue<>();

    // Active Floor Req holds <Req ID, Req> Mapping for requests already assigned to elevators i.e., active requests
    // and which are ready to be served by an elevator
    @Getter
    private static final Map<String, ElevatorRequest> activeRequests = new ConcurrentHashMap<>();

}
