package com.example.demo.cache;

import com.example.demo.model.Elevator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ElevatorCache {
    // CopyOnWriteArrayList or Collections.synchronizedList
    // thread-safe list for cross-service sharing
    // When a write operation occurs (like add or remove), it creates a new copy of the underlying array to ensure that readers working on a previous version are unaffected, but this makes write operations expensive.
    // ideal for read-heavy scenarios where modifications are infrequent. low write frequency, many reads
    // Because Read operations are fast and non-blocking because they operate on a snapshot of the list
        public static final List<Elevator> elevators = new CopyOnWriteArrayList<>();
}
