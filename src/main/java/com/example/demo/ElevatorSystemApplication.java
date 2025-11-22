package com.example.demo;

import com.example.demo.enums.RequestDirection;
import com.example.demo.enums.RequestPriority;
import com.example.demo.model.Elevator;
import com.example.demo.model.ElevatorRequest;
import com.example.demo.repository.ElevatorRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

//@SpringBootApplication
public class ElevatorSystemApplication {

	public static void main(String[] args) {
//		SpringApplication.run(ElevatorSystemApplication.class, args);

//        // start Spring context so beans, repositories and scheduled services are initialized
//        ConfigurableApplicationContext ctx = SpringApplication.run(ElevatorSystemApplication.class, args);

        // obtain controller from Spring context instead of newing it up
//        ElevatorController controller = ctx.getBean(ElevatorController.class);

        ElevatorController controller = new ElevatorController();
        controller.createElevator();

        // Way-1) Globally Request Elevator - scheduler chooses one for you to serve you faster
//        Elevator bestAssignedElevator = controller.requestElevator(RequestDirection.UP, 3, RequestPriority.REGULAR_NORMAL);
//        controller.selectDestinationFloorInsideRequestedElevator(bestAssignedElevator, 3, 7, RequestPriority.REGULAR_NORMAL);

//       // Way-2) Chose an elevator for yourself and wait for it to serve
//        Elevator chosenElevator = new ElevatorRepository().findAll().get(0);//randomly pick one elevator
//        controller.requestParticularElevator(chosenElevator, RequestDirection.UP, 3, RequestPriority.REGULAR_NORMAL);
//        controller.selectDestinationFloorInsideRequestedElevator(chosenElevator, 3, 7, RequestPriority.REGULAR_NORMAL);
//
//        // Way-3) Choose the destination floor directly - scheduler chooses the best elevator to serve you faster
        controller.selectDestinationFloor(3, 10, RequestPriority.REGULAR_NORMAL);
    }

}
