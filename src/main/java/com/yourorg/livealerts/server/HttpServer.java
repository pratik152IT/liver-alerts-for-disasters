package com.yourorg.livealerts.server;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.yourorg.livealerts.model.DisasterEvent;
import com.yourorg.livealerts.service.NotificationService;
import com.yourorg.livealerts.storage.Database;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.staticFiles;

public class HttpServer {
    private final Database db;
    private final Gson gson = new Gson();
    private final NotificationService notificationService;
    private final Set<String> notifiedEventIds = new HashSet<>();

    // Backwards-compatible constructor: allows callers that still use (Database, int)
    // to compile. It creates a default NotificationService (no-arg) and delegates
    // to the main constructor.
    public HttpServer(Database db, int port) {
        this(db, new NotificationService(), port);
    }

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
                }
            } catch (Exception ex) {
                System.err.println("Error retrieving events: " + ex.getMessage());
            }
            return gson.toJson(events);
        });

        get("/health", (req, res) -> "OK");
        
        // API endpoint to send email for a specific disaster event
        get("/send-email", (req, res) -> {
            String id = req.queryParams("id");
            String source = req.queryParams("source");
            if (id == null || source == null) {
                res.status(400);
                return "Missing id or source parameter";
            }
            DisasterEvent event = null;
            try (ResultSet rs = db.listFiltered(null, source)) {
                while (rs.next()) {
                    if (id.equals(rs.getString("id"))) {
                        event = new DisasterEvent();
                        event.setId(rs.getString("id"));
                        event.setTitle(rs.getString("title"));
                        event.setCategory(rs.getString("category"));
                        event.setLat(rs.getDouble("latitude"));
                        event.setLon(rs.getDouble("longitude"));
                        event.setSource(rs.getString("source"));
                        event.setUrl(rs.getString("url"));
                        event.setDate(rs.getString("date"));
                        event.setMagnitude(rs.getDouble("magnitude"));
                        break;
                    }
                }
            } catch (Exception ex) {
                res.status(500);
                return "Error retrieving event: " + ex.getMessage();
            }
            if (event == null) {
                res.status(404);
                return "Event not found";
            }
            try {
                notificationService.notifyNewAlert(event);
                return "Email sent for event: " + event.getTitle();
            } catch (Exception ex) {
                res.status(500);
                return "Failed to send email: " + ex.getMessage();
            }
        });
    }
}