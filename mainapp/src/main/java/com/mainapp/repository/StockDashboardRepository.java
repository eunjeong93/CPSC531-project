package com.mainapp.repository;

import com.mainapp.model.StockDashboardDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockDashboardRepository extends MongoRepository<StockDashboardDocument, String> {

    Optional<StockDashboardDocument> findBySymbol(String symbol);

    List<StockDashboardDocument> findAll();

    List<StockDashboardDocument> findAllByOrderByChangePercentDesc();

    List<StockDashboardDocument> findAllByOrderByChangePercentAsc();

}