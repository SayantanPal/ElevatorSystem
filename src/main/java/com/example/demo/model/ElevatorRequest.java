package com.example.demo.model;

import com.example.demo.enums.RequestDirection;
import com.example.demo.enums.RequestPriority;
import com.example.demo.enums.RequestStatus;
import com.example.demo.enums.RequestType;
import com.example.demo.utility.Helper;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class ElevatorRequest {

    private final String requestId;
    private RequestPriority requestPriority;
    private LocalDateTime requestedAt;

    private int fromSrcFloor;
    private int toDestFloor;

    private RequestDirection requestDirection;
    private final RequestType requestType;

    @Setter
    private RequestStatus requestStatus;

    @Setter
    private Elevator assignedElevator;

    @Getter
    private final AtomicBoolean isEnqueued = new AtomicBoolean(false);


    public ElevatorRequest(RequestPriority requestPriority, RequestType requestType, int fromSrcFloor){
        this.requestId = Helper.generateUUID();
        this.requestPriority = requestPriority;
        this.requestedAt = LocalDateTime.now();
        this.requestType = requestType;
        this.requestStatus = RequestStatus.PENDING;
        this.fromSrcFloor = fromSrcFloor;
     }

    public ElevatorRequest(RequestPriority requestPriority, int fromSrcFloor, RequestDirection requestDirection){
        this(requestPriority, RequestType.FLOOR_DIRECTION_CALL, fromSrcFloor);
        this.requestDirection = requestDirection;
    }

    public ElevatorRequest(RequestPriority requestPriority, int fromSrcFloor, int toDestFloor){
        this(requestPriority, RequestType.DESTINATION_FLOOR_SELECTION, fromSrcFloor);
        this.toDestFloor = toDestFloor;
        if(toDestFloor > fromSrcFloor){
            this.requestDirection = RequestDirection.UP;
        } else if(toDestFloor < fromSrcFloor){
            this.requestDirection = RequestDirection.DOWN;
        } else{
            this.requestDirection = RequestDirection.NONE;
        }
    }

    public boolean isUpward() {
        return this.requestDirection == RequestDirection.UP;
    }

    public boolean isDownward() {
        return this.requestDirection == RequestDirection.DOWN;
    }

    // has it crossed 5 mins from when the user requested the floor
    public boolean hasExpired(){
        return LocalDateTime.now().isAfter(requestedAt.plusMinutes(5));
    }

}
