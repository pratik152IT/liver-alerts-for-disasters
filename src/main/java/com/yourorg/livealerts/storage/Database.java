package com.yourorg.livealerts.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.yourorg.livealerts.model.DisasterEvent;

public class Database {
    private final String dbUrl;
    private final Connection connection;

    public Database(String filePath) throws SQLException {
        dbUrl = "jdbc:sqlite:" + filePath;
        connection = DriverManager.getConnection(dbUrl);
        // Set busy timeout to 5 seconds
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA busy_timeout = 5000;");
        }
        init();
    }

    private void init() throws SQLException {
                String sql = """
                        CREATE TABLE IF NOT EXISTS events (
                            id TEXT,
                            title TEXT,
                            category TEXT,
                            latitude REAL,
                            longitude REAL,
                            source TEXT,
                            url TEXT,
                            date TEXT,
                            magnitude REAL,
                            PRIMARY KEY (id, source)
                        );
                        """;
                try (Statement s = connection.createStatement()) { s.execute(sql); }
    }

    public void upsert(DisasterEvent e) throws SQLException {
        String sql = """
            INSERT INTO events (id,title,category,latitude,longitude,source,url,date,magnitude)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id,source) DO UPDATE SET
             title=excluded.title, category=excluded.category, latitude=excluded.latitude,
             longitude=excluded.longitude, url=excluded.url, date=excluded.date, magnitude=excluded.magnitude;
            """;
        synchronized (this) {
            try (PreparedStatement p = connection.prepareStatement(sql)) {
                p.setString(1, e.getId());
                p.setString(2, e.getTitle());
                p.setString(3, e.getCategory());
                p.setDouble(4, e.getLat());
                p.setDouble(5, e.getLon());
                p.setString(6, e.getSource());
                p.setString(7, e.getUrl());
                p.setString(8, e.getDate());
                p.setDouble(9, e.getMagnitude() != null ? e.getMagnitude() : 0.0);
                p.executeUpdate();
            }
        }
    }

    // Get all events (returns ResultSet; caller must close)
    public ResultSet listAll() throws SQLException {
    Statement s = connection.createStatement();
    return s.executeQuery("SELECT * FROM events ORDER BY date DESC;");
    }

    public ResultSet listFiltered(String category, String source) throws SQLException {
        String sql = "SELECT * FROM events WHERE 1=1";
        if (category != null && !category.isEmpty()) {
            sql += " AND category = ?";
        }
        if (source != null && !source.isEmpty()) {
            sql += " AND source = ?";
        }
        sql += " ORDER BY date DESC;";

        PreparedStatement ps = connection.prepareStatement(sql);
        int paramIndex = 1;
        if (category != null && !category.isEmpty()) {
            ps.setString(paramIndex++, category);
        }
        if (source != null && !source.isEmpty()) {
            ps.setString(paramIndex, source);
        }
        return ps.executeQuery();
    }
}