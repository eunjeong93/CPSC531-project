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

    private static final String BOOTSTRAP = "localhost:9092";
    private static final String TOPIC = "stock-data";
    private static final String API_KEY = "G9L9LHF4VH6CKJAO"; // replace
    private static final String BASE = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&outputsize=compact&apikey=" + API_KEY;
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS).build();
    private static final ObjectMapper mapper = new ObjectMapper();


    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        Producer<String, String> producer = new KafkaProducer<>(props);

        while (true) {
            for (String sym : Symbols.SYMBOLS) {
                try {
                    String url = BASE + "&symbol=" + sym;
                    Request req = new Request.Builder().url(url).get().build();
                    try (Response res = client.newCall(req).execute()) {
                        if (!res.isSuccessful()) {
                            System.err.println("AV request failed for " + sym + " : " + res);
                        } else {
                            String body = res.body().string();
                            JsonNode root = mapper.readTree(body);
                            JsonNode ts = root.get("Time Series (Daily)");
                            if (ts == null) {
                                System.err.println("No Time Series for " + sym + " response: " + body);
                                continue;
                            }
                            Iterator<String> dates = ts.fieldNames();
                            List<String> dateList = new ArrayList<>();
                            while (dates.hasNext()) dateList.add(dates.next());
                            if (dateList.size() < 1) continue;

                            Collections.sort(dateList, Collections.reverseOrder()); // latest first
                            String latest = dateList.get(0);
                            JsonNode latestObj = ts.get(latest);
                            Double close = latestObj.get("4. close").asDouble();
                            Double open = latestObj.get("1. open").asDouble();
                            Long volume = latestObj.get("5. volume").asLong();

                            Double prevClose = null;
                            if (dateList.size() > 1) {
                                String prev = dateList.get(1);
                                prevClose = ts.get(prev).get("4. close").asDouble();
                            }

                            Map<String,Object> msg = new HashMap<>();
                            msg.put("symbol", sym);
                            msg.put("date", latest);
                            msg.put("open", open);
                            msg.put("close", close);
                            msg.put("volume", volume);
                            msg.put("prevClose", prevClose);
                            msg.put("fetchedAt", Instant.now().toString());

                            String jsonMsg = mapper.writeValueAsString(msg);
                            System.out.println("JSON -> " + jsonMsg);
                            ProducerRecord<String,String> record = new ProducerRecord<>(TOPIC, sym, jsonMsg);
                            producer.send(record, (metadata, ex) -> {
                                if (ex != null) ex.printStackTrace();
                            });
                            System.out.println("Produced " + sym + " -> " + latest + " close=" + close);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //Sleep to avoid rate-limits.
                Thread.sleep(15000); // 15s between symbol requests
            }
        }
    }
}
