package com.mainapp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "stockDashboard")
@Data
public class StockDashboardDocument {

    @Id
    private String id;

    private String symbol;
    private String latestDate;
    private Double todayPrice;
    private Double priceChange;
    private Double changePercent;
    private Long volume;
    private Double prevClose;
    private String fetchedAt;
    private Instant updatedAt;

}
