package com.kafkaproducer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafkaproducer.helper.Symbols;
import okhttp3.*;
import org.apache.kafka.clients.producer.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class StockProducer {

    // Kafka broker address
    private static final String BOOTSTRAP = "localhost:9092";

    // Kafka topic where stock data will be published
    private static final String TOPIC = "stock-data";

    // AlphaVantage API key
    private static final String API_KEY = "G9L9LHF4VH6CKJAO";

    // Base URL for fetching daily stock prices
    private static final String BASE = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&outputsize=compact&apikey=" + API_KEY;

    // HTTP client with a reasonable timeout
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS).build();

    // Jackson mapper for JSON parsing and serialization
    private static final ObjectMapper mapper = new ObjectMapper();


    public static void main(String[] args) throws InterruptedException {

        // Kafka producer configuration
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        // Create Kafka producer
        Producer<String, String> producer = new KafkaProducer<>(props);

        // Run continuously to keep streaming stock data
        while (true) {

            // Iterate over all configured stock symbols
            for (String sym : Symbols.SYMBOLS) {
                try {

                    // Build request URL for the current symbol
                    String url = BASE + "&symbol=" + sym;
                    Request req = new Request.Builder().url(url).get().build();

                    // Execute API request
                    try (Response res = client.newCall(req).execute()) {
                        if (!res.isSuccessful()) {
                            // API call failed (network issue or rate limit)
                            System.err.println("AV request failed for " + sym + " : " + res);
                        } else {

                            // Read API response body
                            String body = res.body().string();
                            JsonNode root = mapper.readTree(body);

                            // Extract daily time series
                            JsonNode ts = root.get("Time Series (Daily)");
                            if (ts == null) {

                                // Happens when API limit is hit or response is invalid
                                System.err.println("No Time Series for " + sym + " response: " + body);
                                continue;
                            }

                            // Collect all available dates
                            Iterator<String> dates = ts.fieldNames();
                            List<String> dateList = new ArrayList<>();
                            while (dates.hasNext()) dateList.add(dates.next());
                            if (dateList.size() < 1) continue;

                            // Sort dates so the latest trading day comes first
                            Collections.sort(dateList, Collections.reverseOrder()); // latest first
                            String latest = dateList.get(0);
                            JsonNode latestObj = ts.get(latest);

                            // Extract required fields
                            Double close = latestObj.get("4. close").asDouble();
                            Double open = latestObj.get("1. open").asDouble();
                            Long volume = latestObj.get("5. volume").asLong();

                            // Get previous day close (used later for price comparison)
                            Double prevClose = null;
                            if (dateList.size() > 1) {
                                String prev = dateList.get(1);
                                prevClose = ts.get(prev).get("4. close").asDouble();
                            }

                            // Prepare message payload
                            Map<String,Object> msg = new HashMap<>();
                            msg.put("symbol", sym);
                            msg.put("date", latest);
                            msg.put("open", open);
                            msg.put("close", close);
                            msg.put("volume", volume);
                            msg.put("prevClose", prevClose);
                            msg.put("fetchedAt", Instant.now().toString());

                            // Convert message to JSON
                            String jsonMsg = mapper.writeValueAsString(msg);
                            System.out.println("JSON -> " + jsonMsg);

                            // Send record to Kafka
                            ProducerRecord<String,String> record = new ProducerRecord<>(TOPIC, sym, jsonMsg);
                            producer.send(record, (metadata, ex) -> {
                                if (ex != null) ex.printStackTrace();
                            });
                            System.out.println("Produced " + sym + " -> " + latest + " close=" + close);
                        }
                    }
                } catch (IOException e) {
                    // Covers network issues, JSON parsing errors, etc.
                    e.printStackTrace();
                }
                //Sleep to avoid rate-limits.
                Thread.sleep(15000); // 15s between symbol requests
            }
        }
    }
}
