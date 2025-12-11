package com.mainapp.service;

import com.mainapp.model.StockDashboardDocument;
import com.mainapp.repository.StockDashboardRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
@Service
public class StockDashboardService {
    private final StockDashboardRepository repo;

    public StockDashboardService(StockDashboardRepository repo) {
        this.repo = repo;
    }

    private String formatDollar(Double val) {
        if (val == null) return null;
        return "$" + String.format("%.2f", val);
    }

    private String formatPercent(Double val) {
        if (val == null) return null;
        return String.format(Locale.US, "%.2f%%", val);
    }

    private String formatMillions(Long value) {
        if (value == null) return null;
        if (value >= 1_000_000_000) return String.format("%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format("%.2fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.2fK", value / 1000.0);
        return value.toString();
    }

    // --------------------- API #1 ---------------------
    // /stock-api/info
    public Map<String, Object> getMarketInfo() {
        List<StockDashboardDocument> all = repo.findAll();
        if (all.isEmpty()) return Collections.emptyMap();

        // Leader = highest price
        StockDashboardDocument leader =
                all.stream().max(Comparator.comparing(StockDashboardDocument::getTodayPrice)).orElse(null);

        // Top stock = highest % gain
        StockDashboardDocument top =
                all.stream().max(Comparator.comparing(StockDashboardDocument::getChangePercent)).orElse(null);

        // Worst stock = biggest negative %
        StockDashboardDocument worst =
                all.stream().min(Comparator.comparing(StockDashboardDocument::getChangePercent)).orElse(null);

        Map<String, Object> res = new HashMap<>();
        res.put("market", "United States");
        res.put("leader", leader.getSymbol());
        res.put("topStock", top.getSymbol());
        res.put("topStockPercentage", formatPercent(top.getChangePercent()));
        res.put("worstStock", worst.getSymbol());
        res.put("worstStockPercentage", formatPercent(worst.getChangePercent()));

        return res;
    }

    // --------------------- API #2 ---------------------
    // /stock-api/market-summary
    public List<Map<String, String>> getMarketSummary() {
        List<StockDashboardDocument> all = repo.findAll();

        return all.stream().map(doc -> {
            Map<String, String> m = new HashMap<>();
            m.put("companyName", doc.getSymbol());
            m.put("todayPrice", formatDollar(doc.getTodayPrice()));
            m.put("priceChange", formatDollar(doc.getPriceChange()));
            m.put("change", formatPercent(doc.getChangePercent()));
            return m;
        }).collect(Collectors.toList());
    }

    // --------------------- API #3 ---------------------
    // /stock-api/active-stocks
    public Map<String, Object> getActiveStocks() {

        List<StockDashboardDocument> all = repo.findAll();

        // Biggest Gainers (TOP 5)
        List<Map<String, Object>> gainers = all.stream()
                .filter(x -> x.getChangePercent() != null)
                .sorted(Comparator.comparing(StockDashboardDocument::getChangePercent).reversed())
                .limit(5)
                .map(doc -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("companyName", doc.getSymbol());
                    m.put("price", doc.getTodayPrice());
                    m.put("changePercent", formatPercent(doc.getChangePercent()));
                    m.put("change", doc.getPriceChange());
                    m.put("volume", formatMillions(doc.getVolume()));
                    m.put("rvol", null);  // you can compute RVOL later
                    m.put("float", null); // optional
                    m.put("marketCap", null);
                    return m;
                }).collect(Collectors.toList());

        // Biggest Losers (BOTTOM 5)
        List<Map<String, Object>> losers = all.stream()
                .filter(x -> x.getChangePercent() != null)
                .sorted(Comparator.comparing(StockDashboardDocument::getChangePercent))
                .limit(5)
                .map(doc -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("companyName", doc.getSymbol());
                    m.put("price", doc.getTodayPrice());
                    m.put("changePercent", formatPercent(doc.getChangePercent()));
                    m.put("change", doc.getPriceChange());
                    m.put("volume", formatMillions(doc.getVolume()));
                    m.put("rvol", null);
                    m.put("float", null);
                    m.put("marketCap", null);
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> output = new HashMap<>();
        output.put("biggestGainers", gainers);
        output.put("biggestLosers", losers);
        return output;
    }
}
