package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.BehaviorProfileDTO;
import com.datn.finrisk.core.services.BehavioralProfilingService;
import com.datn.finrisk.core.services.TimeMachineSeederService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    @Autowired private  TimeMachineSeederService timeMachineSeeder;
    private final BehavioralProfilingService profilingService;
    
    @GetMapping("/user/{userId}/behavior-profile")
    public ResponseEntity<BehaviorProfileDTO> getUserBehaviorProfile(
            @PathVariable Long userId) {
        return ResponseEntity.ok(
            profilingService.getReadableProfile(userId));
    }

    @GetMapping("/user/{userId}/anomaly-history")
    public ResponseEntity<?> getAnomalyHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(
            profilingService.getAnomalyHistory(userId, days));
    }

    @PostMapping("/dev/generate-ai-data")
    public ResponseEntity<String> generateAiData() {
 
        String result = timeMachineSeeder.startTimeTravelSimulation();
        return ResponseEntity.ok(result);
    }
}

