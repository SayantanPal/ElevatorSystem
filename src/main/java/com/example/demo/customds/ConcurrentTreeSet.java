package com.example.demo.customds;

import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
*   Operation	        TreeSet + ReadWriteLock	        ConcurrentSkipListSet
    Thread-safety	        ✅ Yes (manual)	                ✅ Yes (automatic)
    Reads (no writers)	    ✅ Multiple readers	            ✅ Fully concurrent
    Reads (with writers)	❌ Blocked by write lock	    ✅ Some reads proceed concurrently
    Writes	                Exclusive, blocks all readers	Fine-grained, concurrent with unrelated reads/writes
    Iteration	            Must acquire read lock	        Weakly consistent, no locks
    Complexity	O(log n)	O(log n)
    Implementation overhead	Manual, error-prone	Automatic
    Scalability	            Good with few writes	        Excellent with many concurrent ops
* */

public class ConcurrentTreeSet {
    private final TreeSet<Integer> set = new TreeSet<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void add(int x) {
        lock.writeLock().lock();
        try {
            set.add(x);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(int x) {
        lock.writeLock().lock();
        try {
            set.remove(x);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Integer higher(int x) {
        lock.readLock().lock();
        try {
            return set.higher(x);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(int x) {
        lock.readLock().lock();
        try {
            return set.contains(x);
        } finally {
            lock.readLock().unlock();
        }
    }
}
