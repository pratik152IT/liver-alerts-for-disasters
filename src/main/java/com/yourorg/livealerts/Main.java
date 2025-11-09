package com.yourorg.livealerts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.yourorg.livealerts.fetcher.EonetFetcher;
import com.yourorg.livealerts.fetcher.UsgsFetcher;
import com.yourorg.livealerts.model.DisasterEvent;
import com.yourorg.livealerts.server.HttpServer;
import com.yourorg.livealerts.storage.Database;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Live Alerts...");

        // ensure data folder exists
        Path data = Paths.get("data");
        if (!Files.exists(data)) Files.createDirectories(data);
        String dbFile = "data/livealerts.db";
        Database db = new Database(dbFile);

        // register fetchers
        List<com.yourorg.livealerts.fetcher.Fetcher> fetchers = List.of(
                new EonetFetcher(),
                new UsgsFetcher()
        );

        // Scheduler: poll every 60 seconds
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        Runnable job = () -> {
            for (var f : fetchers) {
                try {
                    System.out.println("Polling " + f.sourceName() + " ...");
                    List<DisasterEvent> events = f.fetch();
                    for (var e : events) {
                        try { db.upsert(e); } catch (SQLException ex) { ex.printStackTrace(); }
                    }
                    System.out.println("Fetched " + events.size() + " from " + f.sourceName());
                } catch (Exception ex) {
                    System.err.println("Error fetching from " + f.sourceName());
                    ex.printStackTrace();
                }
            }
        };

        // initial run
        job.run();
        // schedule: run every 60 seconds (first run after 60s)
        scheduler.scheduleAtFixedRate(job, 60, 60, TimeUnit.SECONDS);

        // start HTTP server on port 4567
        new HttpServer(db, 4567);

        // add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            System.out.println("Shutting down...");
        }));
    }
}
