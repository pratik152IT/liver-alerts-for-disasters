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

    public Database(String filePath) throws SQLException {
        dbUrl = "jdbc:sqlite:" + filePath;
        init();
    }

    private void init() throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl)) {
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
            try (Statement s = c.createStatement()) { s.execute(sql); }
        }
    }

    public void upsert(DisasterEvent e) throws SQLException {
        String sql = """
            INSERT INTO events (id,title,category,latitude,longitude,source,url,date,magnitude)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id,source) DO UPDATE SET
             title=excluded.title, category=excluded.category, latitude=excluded.latitude,
             longitude=excluded.longitude, url=excluded.url, date=excluded.date, magnitude=excluded.magnitude;
            """;
        try (Connection c = DriverManager.getConnection(dbUrl);
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, e.id);
            p.setString(2, e.title);
            p.setString(3, e.category);
            p.setDouble(4, e.latitude);
            p.setDouble(5, e.longitude);
            p.setString(6, e.source);
            p.setString(7, e.url);
            p.setString(8, e.date);
            p.setDouble(9, e.magnitude);
            p.executeUpdate();
        }
    }

    // Get all events (returns ResultSet; caller must close)
    public ResultSet listAll() throws SQLException {
        Connection c = DriverManager.getConnection(dbUrl);
        Statement s = c.createStatement();
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

        Connection c = DriverManager.getConnection(dbUrl);
        PreparedStatement ps = c.prepareStatement(sql);
        
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