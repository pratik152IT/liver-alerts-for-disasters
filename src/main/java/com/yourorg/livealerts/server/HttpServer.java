package com.yourorg.livealerts.server;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.yourorg.livealerts.storage.Database;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.staticFiles;

public class HttpServer {
    private final Database db;
    private final Gson gson = new Gson();
    private final NotificationService notificationService;
    private final Set<String> notifiedEventIds = new HashSet<>();

    public HttpServer(Database db, NotificationService notificationService, int port) {
        this.db = db;
        this.notificationService = notificationService;
        port(port);
        staticFiles.location("/static"); // serves resources from src/main/resources/static
        // API endpoints
        get("/events", (req, res) -> {
            res.type("application/json");
            List<DisasterEvent> events = new ArrayList<>();
            
            String category = req.queryParams("category");
            String source = req.queryParams("source");
            
            try (ResultSet rs = category == null && source == null ? 
                    db.listAll() : 
                    db.listFiltered(category, source)) {
                while (rs.next()) {
                    DisasterEvent event = new DisasterEvent();
                    event.setId(rs.getString("id"));
                    event.setTitle(rs.getString("title"));
                    event.setCategory(rs.getString("category"));
                    event.setLat(rs.getDouble("latitude"));
                    event.setLon(rs.getDouble("longitude"));
                    event.setSource(rs.getString("source"));
                    event.setUrl(rs.getString("url"));
                    event.setDate(rs.getString("date"));
                    event.setMagnitude(rs.getDouble("magnitude"));
                    events.add(event);
                    
                    // Check if this is a new event that we haven't notified about
                    if (!notifiedEventIds.contains(event.getId())) {
                        notifiedEventIds.add(event.getId());
                        notificationService.notifyNewAlert(event);
                    }
                }
            } catch (Exception ex) {
                System.err.println("Error retrieving events: " + ex.getMessage());
            }
            return gson.toJson(events);
        });

        get("/health", (req, res) -> "OK");
    }
}
