package com.cg.traficlight.service;

import com.cg.traficlight.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrafficLightServiceTest {

    @Mock
    ScheduledExecutorService mockScheduler;

    @Mock
    ScheduledFuture<?> mockFuture;

    private TrafficLightService real;
    private TrafficLightService spy;

    @BeforeEach
    void setUp() throws Exception {
        real = new TrafficLightService();
        spy = spy(real);

        // inject mock scheduler into both real and spy: spy method execution uses spy's fields
        setPrivateField(real, "scheduler", mockScheduler);
        setPrivateField(spy, "scheduler", mockScheduler);

        // make scheduler stubbing lenient so tests that don't trigger scheduling won't fail
        lenient().doReturn(mockFuture).when(mockScheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        // default to not paused so getStatus and schedule behaviors are deterministic
        setPrivateField(real, "paused", false);
        setPrivateField(spy, "paused", false);

        // clear records on the real instance
        ((List<?>) getPrivateField(real, "records", List.class)).clear();
        ((List<?>) getPrivateField(spy, "records", List.class)).clear();
    }

    // reflection helpers
    private void setPrivateField(Object target, String name, Object value) throws Exception {
        // Always use the TrafficLightService class to avoid proxy/spy class issues
        Field f = TrafficLightService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object target, String name, Class<T> type) throws Exception {
        // Always use the TrafficLightService class to avoid proxy/spy class issues
        Field f = TrafficLightService.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private void setCurrentPhaseIndex(Object target, int idx) throws Exception {
        java.util.concurrent.atomic.AtomicInteger ai = getPrivateField(target, "currentPhaseIndex", java.util.concurrent.atomic.AtomicInteger.class);
        ai.set(idx);
    }

    @Test
    void scheduleCurrentPhase_detectsConflictingGreenAndPauses() throws Exception {
        // prepare spy with one movement
        List<Movement> movements = new ArrayList<>();
        movements.add(new Movement(Directions.NORTH, Colors.GREEN, 1000L));
        setPrivateField(spy, "movements", movements);
        setCurrentPhaseIndex(spy, 0);

        // conflicting response: EAST is GREEN while NORTH is active
        Response conflict = new Response();
        conflict.setActiveDirection(Directions.NORTH);
        conflict.setActiveColor(Colors.GREEN);
        EnumMap<Directions, Colors> inactive = new EnumMap<>(Directions.class);
        inactive.put(Directions.EAST, Colors.GREEN);
        inactive.put(Directions.SOUTH, Colors.RED);
        inactive.put(Directions.WEST, Colors.RED);
        conflict.setInactiveState(inactive);
        conflict.setPaused(false);

        // stub getStatus on spy to return conflict
        lenient().doReturn(conflict).when(spy).getStatus();

        Method sched = TrafficLightService.class.getDeclaredMethod("scheduleCurrentPhase", long.class);
        sched.setAccessible(true);

        try {
            sched.invoke(spy, 0L);
            fail("Expected IllegalStateException due to conflicting GREEN lights");
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            assertNotNull(cause);
            assertInstanceOf(IllegalStateException.class, cause);
            assertTrue(cause.getMessage().contains("Conflicting GREEN lights"));
        }

        // service should have been paused
        assertTrue((Boolean) getPrivateField(spy, "paused", Boolean.class));
    }

    @Test
    void getStatus_returnsExpectedStateForCurrentPhase() throws Exception {
        // use real instance here
        List<Movement> movements = new ArrayList<>();
        movements.add(new Movement(Directions.NORTH, Colors.GREEN, 1000L));
        setPrivateField(real, "movements", movements);
        setCurrentPhaseIndex(real, 0);
        setPrivateField(real, "paused", false);

        Response status = real.getStatus();

        assertEquals(Directions.NORTH, status.getActiveDirection());
        assertEquals(Colors.GREEN, status.getActiveColor());
        assertFalse(status.isPaused());

        EnumMap<Directions, Colors> inactive = status.getInactiveState();
        assertEquals(Colors.RED, inactive.get(Directions.EAST));
        assertEquals(Colors.RED, inactive.get(Directions.SOUTH));
        assertEquals(Colors.RED, inactive.get(Directions.WEST));
    }

    @Test
    void setSequence_appliesNewSequenceAndSchedules() throws Exception {
        // use a real SignalSequence instead of mocking
        SignalSequence seq = new SignalSequence();
        seq.setTimeGreenNS(7);
        seq.setTimeYellowNS(2);
        seq.setTimeGreenEW(5);
        seq.setTimeYellowEW(2);

        // ensure spy is not paused so scheduling occurs
        setPrivateField(spy, "paused", false);

        real.setSequence(seq);

        @SuppressWarnings("unchecked")
        List<Movement> movements = (List<Movement>) getPrivateField(real, "movements", List.class);
        assertNotNull(movements);
        assertEquals(8, movements.size());

        Movement first = movements.get(0);
        assertEquals(Directions.NORTH, first.getDirection());
        assertEquals(Colors.GREEN, first.getColors());
        assertEquals(TimeUnit.SECONDS.toMillis(7L), first.getDurationMillis());

        int idx = ((java.util.concurrent.atomic.AtomicInteger) getPrivateField(real, "currentPhaseIndex", java.util.concurrent.atomic.AtomicInteger.class)).get();
        assertEquals(0, idx);

        // verify scheduler was asked to schedule by checking the scheduledFuture field was set to our mock on the real instance
        ScheduledFuture<?> scheduled = getPrivateField(real, "scheduledFuture", ScheduledFuture.class);
        assertNotNull(scheduled, "Expected scheduledFuture to be set after setSequence");
        assertEquals(mockFuture, scheduled, "scheduledFuture should reference the mock future returned by the scheduler");
    }

    @Test
    void pause_cancelsScheduledAndSetsPaused() throws Exception {
        when(mockFuture.isDone()).thenReturn(false);
        setPrivateField(real, "scheduledFuture", mockFuture);
        setPrivateField(real, "paused", false);

        real.pause();

        assertTrue((Boolean) getPrivateField(real, "paused", Boolean.class));
        verify(mockFuture).cancel(false);
        assertNull((ScheduledFuture<?>) getPrivateField(real, "scheduledFuture", ScheduledFuture.class));
    }

    @Test
    void resume_schedulesCurrentPhaseWhenPaused() throws Exception {
        List<Movement> movements = new ArrayList<>();
        movements.add(new Movement(Directions.NORTH, Colors.GREEN, 1000L));
        setPrivateField(real, "movements", movements);
        setCurrentPhaseIndex(real, 0);
        setPrivateField(real, "paused", true);

        real.resume();

        assertFalse((Boolean) getPrivateField(real, "paused", Boolean.class));
        // verify scheduledFuture was set on the real instance
        ScheduledFuture<?> scheduled = getPrivateField(real, "scheduledFuture", ScheduledFuture.class);
        assertNotNull(scheduled);
        assertEquals(mockFuture, scheduled);
    }

    @Test
    void getTimingHistory_limitsAndReversesRecords() throws Exception {
        @SuppressWarnings("unchecked")
        List<TraficLightHistory> recs = (List<TraficLightHistory>) getPrivateField(real, "records", List.class);
        recs.clear();
        TraficLightHistory h1 = new TraficLightHistory();
        h1.setId(1L);
        TraficLightHistory h2 = new TraficLightHistory();
        h2.setId(2L);
        TraficLightHistory h3 = new TraficLightHistory();
        h3.setId(3L);
        recs.add(h1);
        recs.add(h2);
        recs.add(h3);

        // limit to 2 most recent records
        setPrivateField(real, "maxRecordSize", 2L);

        List<TraficLightHistory> result = real.getTimingHistory();
        assertEquals(2, result.size());
        assertEquals(3L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }
}
