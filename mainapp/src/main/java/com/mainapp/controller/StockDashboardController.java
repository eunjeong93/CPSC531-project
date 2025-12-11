package com.mainapp.controller;

import com.mainapp.service.StockDashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stock-api")
public class StockDashboardController {
    private final StockDashboardService service;

    public StockDashboardController(StockDashboardService service) {
        this.service = service;
    }

    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        return service.getMarketInfo();
    }

    @GetMapping("/market-summary")
    public List<Map<String, String>> getSummary() {
        return service.getMarketSummary();
    }

    @GetMapping("/active-stocks")
    public Map<String, Object> getActive() {
        return service.getActiveStocks();
    }
}
