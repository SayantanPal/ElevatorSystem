package com.example.demo.utility;

import com.example.demo.IConstants;
import com.example.demo.enums.RequestType;
import com.example.demo.model.ElevatorRequest;

public class Validator {

    public static boolean isValidRequest(ElevatorRequest request) {
        int srcFloor = request.getFromSrcFloor();
        boolean isValid = floorWithinValidRange(srcFloor);
        if(request.getRequestType() == RequestType.DESTINATION_FLOOR_SELECTION) {
            int destFloor = request.getToDestFloor();
            isValid = isValid
                    && floorWithinValidRange(destFloor)
                    && (srcFloor != destFloor);
        }
        return isValid;
    }

    private static boolean floorWithinValidRange(int floor) {
        return floor >= IConstants.BASE_FLOOR && floor <= IConstants.MAX_FLOOR_COUNT;
    }
}
