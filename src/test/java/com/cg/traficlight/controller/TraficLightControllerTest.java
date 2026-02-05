package com.cg.traficlight.controller;

import com.cg.traficlight.model.*;
import com.cg.traficlight.service.TrafficLightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TraficLightControllerTest {

    @Mock
    private TrafficLightService service;

    @InjectMocks
    private TraficLightController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void callGetStatusService() throws Exception {
        Response resp = new Response();
        resp.setActiveDirection(Directions.NORTH);
        resp.setActiveColor(Colors.GREEN);
        resp.setPaused(false);
        EnumMap<Directions, Colors> map = new EnumMap<>(Directions.class);
        map.put(Directions.EAST, Colors.RED);
        map.put(Directions.SOUTH, Colors.RED);
        map.put(Directions.WEST, Colors.RED);
        resp.setInactiveState(map);

        when(service.getStatus()).thenReturn(resp);

        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service, times(1)).getStatus();
    }

    @Test
    void callSetSequence() throws Exception {
        String json = "{\"timeGreenNS\":5,\"timeYellowNS\":2,\"timeGreenEW\":4,\"timeYellowEW\":2}";

        mockMvc.perform(post("/api/v1/sequence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("Sequence updated"));

        ArgumentCaptor<SignalSequence> captor = ArgumentCaptor.forClass(SignalSequence.class);
        verify(service, times(1)).setSequence(captor.capture());
        SignalSequence captured = captor.getValue();
        assertThat(captured.getTimeGreenNS()).isEqualTo(5);
        assertThat(captured.getTimeYellowNS()).isEqualTo(2);
        assertThat(captured.getTimeGreenEW()).isEqualTo(4);
        assertThat(captured.getTimeYellowEW()).isEqualTo(2);
    }

    @Test
    void callPauseService() throws Exception {
        mockMvc.perform(get("/api/v1/pause"))
                .andExpect(status().isOk())
                .andExpect(content().string("Paused"));
        verify(service, times(1)).pause();
    }

    @Test
    void callResumeService() throws Exception {
        mockMvc.perform(get("/api/v1/resume"))
                .andExpect(status().isOk())
                .andExpect(content().string("Resumed"));
        verify(service, times(1)).resume();
    }

    @Test
    void callGetHistoryService() throws Exception {
        List<TraficLightHistory> list = new ArrayList<>();
        TraficLightHistory h = new TraficLightHistory();
        h.setId(1L);
        h.setDirection(Directions.NORTH);
        h.setColors(Colors.GREEN);
        h.setDurationSeconds(5000L);
        h.setTimestamp(LocalDateTime.now());
        list.add(h);

        when(service.getTimingHistory()).thenReturn(list);

        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service, times(1)).getTimingHistory();
    }
}
