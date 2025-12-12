package com.example.demo.repository;

import com.example.demo.cache.ElevatorCache;
import com.example.demo.model.Elevator;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ElevatorRepository {
    // maintain a mapping for quick lookup by id (backed from ElevatorCache)
    @Getter
    public Map<String, Elevator> elevatorTableInDB = new ConcurrentHashMap<>();

    public void save(Elevator elevator){
        elevatorTableInDB.put(elevator.getElevatorId(), elevator);
        // ensure it's present in cache list as well
        if (!ElevatorCache.elevators.contains(elevator)) { // if not already present in cache
            ElevatorCache.elevators.add(elevator); // populate cache
        }
    }

    public Elevator findById(String id){
        return elevatorTableInDB.getOrDefault(id, null);
    }

    public List<Elevator> findAll(){
        // return snapshot of values stored in map for determinism
        return new ArrayList<>(elevatorTableInDB.values());
    }

    public int findCountOfElevators(){
        return this.findAll().size();
    }
}
