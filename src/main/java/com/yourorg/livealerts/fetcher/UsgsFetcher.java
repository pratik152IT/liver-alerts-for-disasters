package com.yourorg.livealerts.fetcher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yourorg.livealerts.model.DisasterEvent;

public class UsgsFetcher implements Fetcher {
    // all earthquakes in last day (geojson)
    private static final String API = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson";

    @Override
    public String sourceName() { return "USGS"; }

    @Override
    public List<DisasterEvent> fetch() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(new URI(API)).GET().build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
        List<DisasterEvent> out = new ArrayList<>();
        for (JsonElement featEl : root.getAsJsonArray("features")) {
            JsonObject feat = featEl.getAsJsonObject();
            JsonObject prop = feat.getAsJsonObject("properties");
            JsonObject geom = feat.getAsJsonObject("geometry");
            DisasterEvent d = new DisasterEvent();
            d.id = feat.get("id").getAsString();
            d.title = prop.get("title").getAsString();
            d.category = "earthquake";
            JsonArray coords = geom.getAsJsonArray("coordinates");
            d.longitude = coords.get(0).getAsDouble();
            d.latitude = coords.get(1).getAsDouble();
            d.date = prop.has("time") ? String.valueOf(prop.get("time").getAsLong()) : "";
            d.source = sourceName();
            d.url = prop.has("url") ? prop.get("url").getAsString() : API;
            d.magnitude = prop.has("mag") && !prop.get("mag").isJsonNull() ? prop.get("mag").getAsDouble() : 0.0;
            out.add(d);
        }
        return out;
    }
}
