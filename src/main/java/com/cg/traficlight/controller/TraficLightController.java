package com.cg.traficlight.controller;

import com.cg.traficlight.model.LightSequence;
import com.cg.traficlight.model.State;
import com.cg.traficlight.model.TraficLightHistory;
import com.cg.traficlight.service.TrafficLightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1")
public class TraficLightController {

    @Autowired
    private TrafficLightService service;

    @GetMapping("/status")
    public ResponseEntity<State> status() {
        return ResponseEntity.ok(service.getStatus());
    }

    @PostMapping("/sequence")
    public ResponseEntity<String> setSequence(@RequestBody LightSequence req) {
        service.setSequence(req);
        return ResponseEntity.ok("Sequence updated");
    }

    @GetMapping("/pause")
    public ResponseEntity<String> pause() {
        service.pause();
        return ResponseEntity.ok("Paused");
    }

    @GetMapping("/resume")
    public ResponseEntity<String> resume() {
        service.resume();
        return ResponseEntity.ok("Resumed");
    }

    @GetMapping("/history")
    public List<TraficLightHistory> getHistory() {
        return service.getTimingHistory();
    }
}
