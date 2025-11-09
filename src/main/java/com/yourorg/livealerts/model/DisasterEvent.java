package com.yourorg.livealerts.model;

public class DisasterEvent {
    private String id;
    private String title;
    private String category; // e.g., wildfire, earthquake, storm
    private double latitude;
    private double longitude;
    private String source; // EONET, USGS, GDACS
    private String url; // source url
    private String date; // ISO string or epoch string
    private Double magnitude; // optional - for earthquakes

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getLat() { return latitude; }
    public void setLat(double latitude) { this.latitude = latitude; }

    public double getLon() { return longitude; }
    public void setLon(double longitude) { this.longitude = longitude; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public Double getMagnitude() { return magnitude; }
    public void setMagnitude(Double magnitude) { this.magnitude = magnitude; }
}
