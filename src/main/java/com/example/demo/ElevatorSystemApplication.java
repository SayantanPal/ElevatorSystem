package com.example.demo;

import com.example.demo.cache.ElevatorCache;
import com.example.demo.controller.ElevatorController;
import com.example.demo.controller.ElevatorController1;
import com.example.demo.enums.RequestDirection;
import com.example.demo.enums.RequestPriority;
import com.example.demo.model.Elevator;
import com.example.demo.repository.ElevatorRepository;

import java.util.List;
import java.util.Map;

//@SpringBootApplication
public class ElevatorSystemApplication {

	public static void main(String[] args) {
//		SpringApplication.run(ElevatorSystemApplication.class, args);

//        // start Spring context so beans, repositories and scheduled services are initialized
//        ConfigurableApplicationContext ctx = SpringApplication.run(ElevatorSystemApplication.class, args);

        // obtain controller from Spring context instead of newing it up
//        ElevatorController controller = ctx.getBean(ElevatorController.class);

        ElevatorController1 controller = new ElevatorController1();

//        for( Elevator e: ElevatorCache.elevators) {
//            System.out.println("Cache Elevator : " + e);
//        }

        controller.selectDestinationFloorOutsideElevator(13, 7, RequestPriority.REGULAR_NORMAL);
        controller.selectDestinationFloorOutsideElevator(4, 10, RequestPriority.REGULAR_NORMAL);
        controller.selectDestinationFloorOutsideElevator(5, 9, RequestPriority.REGULAR_NORMAL);
        controller.selectDestinationFloorOutsideElevator(7, 13, RequestPriority.REGULAR_NORMAL);
        controller.selectDestinationFloorOutsideElevator(12, 15, RequestPriority.REGULAR_NORMAL);
        controller.selectDestinationFloorOutsideElevator(6, 4, RequestPriority.REGULAR_NORMAL);

        for( Elevator e: ElevatorCache.elevators) {
            System.out.println("Cache Elevator : " + e);
//            System.out.println("Cache Elevator's assigned floor list : " + e.getAssignedFloors());
        }

//         Way-1) Globally Request Elevator - scheduler chooses one for you to serve you faster
//        Elevator elevator = controller.requestElevatorFromScheduler(RequestDirection.UP, 3, RequestPriority.REGULAR_NORMAL);
//        if(elevator != null) {
//            controller.selectDestinationFloorInsideRequestedElevator(elevator, 3, 7, RequestPriority.REGULAR_NORMAL);
//        }

//       // Way-2) Chose an elevator for yourself and wait for it to serve
//        Elevator chosenElevator = new ElevatorRepository().findAll().get(0);//randomly pick one elevator
//        controller.requestSelfChosenElevator(chosenElevator, RequestDirection.UP, 3, RequestPriority.REGULAR_NORMAL);
//        controller.selectDestinationFloorInsideRequestedElevator(chosenElevator, 3, 7, RequestPriority.REGULAR_NORMAL);
//
//        // Way-3) Choose the destination floor directly - scheduler chooses the best elevator to serve you faster
//        controller.selectDestinationFloor(3, 10, RequestPriority.REGULAR_NORMAL);


    }

}
