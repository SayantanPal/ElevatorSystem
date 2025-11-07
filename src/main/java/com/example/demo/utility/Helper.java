package com.example.demo.utility;

import com.example.demo.cache.UserRequestCache;
import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Helper {

    /*
    * It is already thread-safe and singleton-like by nature — since static methods belong to the class, not an instance.
    * There is no instance field (no private data to preserve or share).
    * All methods are pure — only performs computation or fetches from static caches.
    * It’s stateless and side-effect-free (except reading shared data).
    */


    public static boolean checkElevatorMovingInSameDirectionAsFloorReq(Elevator elevator, ElevatorRequest request){
        return (elevator.isMovingUp() && request.isUpward())
                || (elevator.isMovingDown() && request.isDownward());
    }

    public static String generateUUID(){
        return UUID.randomUUID().toString();
    }

    // fetch all elevator requests made by user whose requested floor matches input floor
    public static List<ElevatorRequest> getActiveRequestsFromFloor(int floor){
        return UserRequestCache.getActiveRequests().values().stream()
                                .filter(request -> request.getFromSrcFloor() == floor)
                                .toList();
    }

    // fetch all elevator requests made by user whose requested floor matches input floor
    public static List<ElevatorRequest> getRequestsToFloor(int floor){
        return UserRequestCache.getActiveRequests().values().stream()
                                .filter(request -> request.getToDestFloor() == floor)
                                .toList();
    }

    public static void makePendingRequestActiveForServing(ElevatorRequest request){
        // These structures are individually thread-safe,
        // but a compound operation involving both (add in one, remove from another) is not atomic across them
        // unless you explicitly control the order and condition.
        ElevatorRequest prevExisting = UserRequestCache.getActiveRequests().putIfAbsent(request.getRequestId(), request);
        if(Objects.isNull(prevExisting)){ // if Does not exist previously
            UserRequestCache.getPendingRequests().remove(request);
        }
    }
}
