package com.warehouse.dashboard.controller;

import com.warehouse.dashboard.model.DashboardStats;
import com.warehouse.dashboard.service.AlertStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final AlertStatsService statsService;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", statsService.buildStats());
        return "dashboard";
    }

    @GetMapping("/api/dashboard/stats")
    @ResponseBody
    public DashboardStats stats() {
        return statsService.buildStats();
    }
}
