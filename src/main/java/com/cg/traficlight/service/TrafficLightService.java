package com.cg.traficlight.service;

import com.cg.traficlight.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        Thread t = new Thread(r, "traffic-light");
        t.setDaemon(true);
        return t;
    });
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger currentPhaseIndex = new AtomicInteger(0);
    @Value("${max.record.size:10}")
    private Long maxRecordSize;
    private volatile List<Phase> phases = new ArrayList<>();

    private List<TraficLightHistory> records = new ArrayList<>();

    private volatile ScheduledFuture<?> scheduledFuture;

    private LocalDateTime stateStartTime = LocalDateTime.now();

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

    public void setSequence(LightSequence req) {
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
        List<Phase> list = new ArrayList<>();
        list.add(new Phase(Directions.NORTH, Colors.GREEN, TimeUnit.SECONDS.toMillis(nsGreenSec)));
        list.add(new Phase(Directions.NORTH, Colors.YELLOW, TimeUnit.SECONDS.toMillis(nsYellowSec)));
        list.add(new Phase(Directions.EAST, Colors.GREEN, TimeUnit.SECONDS.toMillis(ewGreenSec)));
        list.add(new Phase(Directions.EAST, Colors.YELLOW, TimeUnit.SECONDS.toMillis(ewYellowSec)));
        list.add(new Phase(Directions.SOUTH, Colors.GREEN, TimeUnit.SECONDS.toMillis(ewGreenSec)));
        list.add(new Phase(Directions.SOUTH, Colors.YELLOW, TimeUnit.SECONDS.toMillis(ewYellowSec)));
        list.add(new Phase(Directions.WEST, Colors.GREEN, TimeUnit.SECONDS.toMillis(ewGreenSec)));
        list.add(new Phase(Directions.WEST, Colors.YELLOW, TimeUnit.SECONDS.toMillis(ewYellowSec)));
        this.phases = List.copyOf(list);
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

    public State getStatus() {
        State s = new State();
        List<Phase> snapshot = phases;
        int idx = currentPhaseIndex.get() % snapshot.size();
        Phase p = snapshot.get(idx);
        s.setActiveDirection(p.getDirection());
        if (p.getDirection() == Directions.NORTH) {
            s.setActiveColor(p.getColors());
        } else if (p.getDirection() == Directions.EAST) {
            s.setActiveColor(p.getColors());
        } else if (p.getDirection() == Directions.SOUTH) {
            s.setActiveColor(p.getColors());
        } else {
            s.setActiveColor(p.getColors());
        }
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
        List<Phase> snapshot = phases;
        int idx = currentPhaseIndex.get() % snapshot.size();
        Phase current = snapshot.get(idx);
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
        return records.reversed().stream().limit(maxRecordSize).toList();
    }

}
