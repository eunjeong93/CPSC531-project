package com.sparkprocessor.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.bson.Document;

import java.sql.Timestamp;
import java.util.*;

import static org.apache.spark.sql.functions.*;

public class StockStreamProcessor {

    public static void main(String[] args) throws Exception {

        SparkSession spark = SparkSession.builder()
                .appName("StockStreamProcessor")
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");

        // 1) Kafka source
        Dataset<Row> kafkaDf = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", "localhost:9092")
                .option("subscribe", "stock-data")
                .option("startingOffsets", "earliest")
                .load();

        // 2) JSON schema that matches your producer
        StructType stockSchema = new StructType()
                .add("symbol", DataTypes.StringType)
                .add("date", DataTypes.StringType)
                .add("open", DataTypes.DoubleType)
                .add("close", DataTypes.DoubleType)
                .add("volume", DataTypes.LongType)
                .add("prevClose", DataTypes.DoubleType)
                .add("fetchedAt", DataTypes.StringType);

        Dataset<Row> jsonDf = kafkaDf
                .selectExpr("CAST(value AS STRING) as jsonStr");

        Dataset<Row> stockDf = jsonDf
                .select(from_json(col("jsonStr"), stockSchema).alias("data"))
                .select("data.*");

        // 3) Add metrics
        Dataset<Row> stockWithMetrics = stockDf
                .withColumn("change",
                        when(col("prevClose").isNotNull(),
                                col("close").minus(col("prevClose")))
                                .otherwise(lit(null)))
                .withColumn("changePercent",
                        when(col("prevClose").isNotNull(),
                                col("change").divide(col("prevClose")).multiply(100))
                                .otherwise(lit(null)))
                .withColumn("ingestedAt", current_timestamp());

        // 4) Write to Mongo via foreachBatch
        StreamingQuery query = stockWithMetrics.writeStream()
                .outputMode("append")
                .option("checkpointLocation", "/tmp/spark-stock-checkpoint")
                .foreachBatch((batchDf, batchId) -> {

                    System.out.println("Processing batch: " + batchId);

                    batchDf.persist();

                    if (batchDf.isEmpty()) {
                        batchDf.unpersist();
                        return;
                    }

                    try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
                        MongoDatabase db = mongoClient.getDatabase("stockdb");
                        MongoCollection<Document> rawCol = db.getCollection("stockRaw");
                        MongoCollection<Document> dashboardCol = db.getCollection("stockDashboard");

                        List<Row> rows = batchDf.collectAsList();

                        List<Document> rawDocs = new ArrayList<>();
                        Map<String, Document> latestPerSymbol = new HashMap<>();

                        for (Row row : rows) {
                            String symbol = row.getAs("symbol");
                            String date = row.getAs("date");

                            Double open = getDouble(row, "open");
                            Double close = getDouble(row, "close");
                            Long volume = getLong(row, "volume");
                            Double prevClose = getDouble(row, "prevClose");
                            Double change = getDouble(row, "change");
                            Double changePercent = getDouble(row, "changePercent");
                            String fetchedAt = row.getAs("fetchedAt");
                            Timestamp ingestedAt = row.getAs("ingestedAt");

                            // ----- stockRaw document -----
                            Document rawDoc = new Document("symbol", symbol)
                                    .append("date", date)
                                    .append("open", open)
                                    .append("close", close)
                                    .append("volume", volume)
                                    .append("prevClose", prevClose)
                                    .append("change", change)
                                    .append("changePercent", changePercent)
                                    .append("fetchedAt", fetchedAt)
                                    .append("ingestedAt", ingestedAt);

                            rawDocs.add(rawDoc);

                            Document dashDoc = new Document("symbol", symbol)
                                    .append("latestDate", date)
                                    .append("todayPrice", close)
                                    .append("priceChange", change)
                                    .append("changePercent", changePercent)
                                    .append("volume", volume)
                                    .append("prevClose", prevClose)
                                    .append("fetchedAt", fetchedAt)
                                    .append("updatedAt", ingestedAt);

                            Document existing = latestPerSymbol.get(symbol);
                            if (existing == null) {
                                latestPerSymbol.put(symbol, dashDoc);
                            } else {
                                String existingDate = existing.getString("latestDate");
                                if (existingDate == null || date.compareTo(existingDate) >= 0) {
                                    latestPerSymbol.put(symbol, dashDoc);
                                }
                            }
                        }

                        // bulk insert raw records
                        if (!rawDocs.isEmpty()) {
                            rawCol.insertMany(rawDocs);
                        }

                        // upsert dashboard per symbol
                        ReplaceOptions upsert = new ReplaceOptions().upsert(true);
                        for (Document dashDoc : latestPerSymbol.values()) {
                            String symbol = dashDoc.getString("symbol");
                            dashboardCol.replaceOne(
                                    new Document("symbol", symbol),
                                    dashDoc,
                                    upsert
                            );
                        }

                    } finally {
                        batchDf.unpersist();
                    }

                })
                .start();

        query.awaitTermination();
    }

    private static Double getDouble(Row row, String field) {
        int idx = row.fieldIndex(field);
        if (row.isNullAt(idx)) return null;
        return row.getDouble(idx);
    }

    private static Long getLong(Row row, String field) {
        int idx = row.fieldIndex(field);
        if (row.isNullAt(idx)) return null;
        return row.getLong(idx);
    }
}
