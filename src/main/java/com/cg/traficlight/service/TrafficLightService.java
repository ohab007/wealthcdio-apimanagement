package com.cg.traficlight.service;

import com.cg.traficlight.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TrafficLightService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "traffic-scheduler");
        t.setDaemon(true);
        return t;
    });
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger currentPhaseIndex = new AtomicInteger(0);
    @Value("${max.record.size:10}")
    private Long maxRecordSize;
    private volatile List<Movement> movements = new ArrayList<>();

    private List<TraficLightHistory> records = new ArrayList<>();

    private volatile ScheduledFuture<?> scheduledFuture;

    private volatile boolean paused = false;

    public TrafficLightService() {
    }

    @PostConstruct
    public void init() {
        applySequence(20, 3, 20, 3);
        startCycle();
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    public void setSequence(SignalSequence req) {
        lock.lock();
        try {
            applySequence(req.getTimeGreenNS(), req.getTimeYellowNS(),
                    req.getTimeGreenEW(), req.getTimeYellowEW());
            currentPhaseIndex.set(0);
            cancelScheduled();
            if (!paused) {
                scheduleCurrentPhase(0); // start immediately
            }
        } finally {
            lock.unlock();
        }
    }

    private void applySequence(long nsGreenSec, long nsYellowSec, long ewGreenSec, long ewYellowSec) {
        List<Movement> list = new ArrayList<>();
        list.add(new Movement(Directions.NORTH, Colors.GREEN, TimeUnit.SECONDS.toMillis(nsGreenSec)));
        list.add(new Movement(Directions.NORTH, Colors.YELLOW, TimeUnit.SECONDS.toMillis(nsYellowSec)));
        list.add(new Movement(Directions.EAST, Colors.GREEN, TimeUnit.SECONDS.toMillis(ewGreenSec)));
        list.add(new Movement(Directions.EAST, Colors.YELLOW, TimeUnit.SECONDS.toMillis(ewYellowSec)));
        list.add(new Movement(Directions.SOUTH, Colors.GREEN, TimeUnit.SECONDS.toMillis(ewGreenSec)));
        list.add(new Movement(Directions.SOUTH, Colors.YELLOW, TimeUnit.SECONDS.toMillis(ewYellowSec)));
        list.add(new Movement(Directions.WEST, Colors.GREEN, TimeUnit.SECONDS.toMillis(ewGreenSec)));
        list.add(new Movement(Directions.WEST, Colors.YELLOW, TimeUnit.SECONDS.toMillis(ewYellowSec)));
        this.movements = List.copyOf(list);
    }

    public void pause() {
        lock.lock();
        try {
            if (paused) return;
            paused = true;
            cancelScheduled();
        } finally {
            lock.unlock();
        }
    }

    public void resume() {
        lock.lock();
        try {
            if (!paused) return;
            paused = false;
            scheduleCurrentPhase(0);
        } finally {
            lock.unlock();
        }
    }

    public Response getStatus() {
        Response s = new Response();
        EnumMap<Directions, Colors> lights = new EnumMap<>(Directions.class);
        List<Movement> movementsList = movements;
        int idx = currentPhaseIndex.get() % movementsList.size();
        Movement p = movementsList.get(idx);
        s.setActiveDirection(p.getDirection());
        if (p.getDirection() == Directions.NORTH) {
            s.setActiveColor(p.getColors());
            lights.put(Directions.EAST, Colors.RED);
            lights.put(Directions.SOUTH, Colors.RED);
            lights.put(Directions.WEST, Colors.RED);
        } else if (p.getDirection() == Directions.EAST) {
            s.setActiveColor(p.getColors());
            lights.put(Directions.NORTH, Colors.RED);
            lights.put(Directions.SOUTH, Colors.RED);
            lights.put(Directions.WEST, Colors.RED);
        } else if (p.getDirection() == Directions.SOUTH) {
            s.setActiveColor(p.getColors());
            lights.put(Directions.EAST, Colors.RED);
            lights.put(Directions.NORTH, Colors.RED);
            lights.put(Directions.WEST, Colors.RED);
        } else {
            s.setActiveColor(p.getColors());
            lights.put(Directions.EAST, Colors.RED);
            lights.put(Directions.SOUTH, Colors.RED);
            lights.put(Directions.NORTH, Colors.RED);
        }
        s.setInactiveState(lights);
        s.setPaused(paused);
        return s;
    }

    private void startCycle() {
        lock.lock();
        try {
            cancelScheduled();
            if (!paused) scheduleCurrentPhase(0);
        } finally {
            lock.unlock();
        }
    }

    private void scheduleCurrentPhase(long delayMillis) {
        List<Movement> movementsList = movements;
        if (movementsList.isEmpty()) return;
        int idx = currentPhaseIndex.get() % movementsList.size();
        Movement current = movementsList.get(idx);
        Response status = getStatus();
        boolean anyInactiveGreen = status.getInactiveState().values().stream()
                .anyMatch(c -> c == Colors.GREEN);
        if (anyInactiveGreen) {
            lock.lock();
            try {
                paused = true;
                cancelScheduled();
            } finally {
                lock.unlock();
            }
            throw new IllegalStateException("Conflicting GREEN lights detected");
        }

        long duration = current.getDurationMillis();
        TraficLightHistory history = new TraficLightHistory();
        history.setId(currentPhaseIndex.longValue() + 1);
        history.setColors(current.getColors());
        history.setDirection(current.getDirection());
        history.setTimestamp(LocalDateTime.now());
        history.setDurationSeconds(duration);
        records.add(history);

        if (duration <= 0) {
            currentPhaseIndex.incrementAndGet();
            scheduleCurrentPhase(0);
            return;
        }

        scheduledFuture = scheduler.schedule(() -> {
            lock.lock();
            try {
                if (paused) return;
                currentPhaseIndex.incrementAndGet();
                scheduleCurrentPhase(0);
            } finally {
                lock.unlock();
            }
        }, duration + delayMillis, TimeUnit.MILLISECONDS);

    }

    private void cancelScheduled() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = null;
    }

    public List<TraficLightHistory> getTimingHistory() {
        List<TraficLightHistory> copy = new ArrayList<>(records);
        java.util.Collections.reverse(copy);
        long limit = (maxRecordSize == null) ? copy.size() : maxRecordSize;
        return copy.stream().limit(limit).toList();
    }
}
