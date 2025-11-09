package com.yourorg.livealerts.fetcher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yourorg.livealerts.model.DisasterEvent;

public class EonetFetcher implements Fetcher {
    private static final String API = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120 Safari/537.36";

    @Override
    public String sourceName() { return "EONET"; }

    @Override
    public List<DisasterEvent> fetch() throws Exception {
        // Request timeouts
        RequestConfig reqCfg = RequestConfig.custom()
                // .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(15))
                .build();
        // Build SSL socket factory that prefers TLSv1.3 then TLSv1.2 (system truststore)
        // Build SSL socket factory that prefers TLSv1.3 then TLSv1.2 (system truststore)
        var sslContext = SSLContexts.createSystemDefault();
        var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .build();
        var connManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setUserAgent(UA)
                .setDefaultRequestConfig(reqCfg)
                .setConnectionManager(connManager)
                .build()) {

            HttpGet request = new HttpGet(API);
            request.addHeader("Accept", "application/json");
            request.addHeader("User-Agent", UA);

            List<DisasterEvent> out = httpClient.execute(request, response -> {
                int status = response.getCode();
                if (status < 200 || status >= 300) {
                    // return empty list rather than throw to keep service running
                    System.err.println("EONET fetch failed: HTTP " + status);
                    try {
                        EntityUtils.consume(response.getEntity());
                    } catch (java.io.IOException | RuntimeException ignored) {
                        // ignore cleanup errors
                    }
                    return new ArrayList<>();
                }

                if (response.getEntity() == null) {
                    System.err.println("EONET fetch failed: empty response entity");
                    return new ArrayList<>();
                }

                String jsonStr = EntityUtils.toString(response.getEntity());
                JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                List<DisasterEvent> outList = new ArrayList<>();
                JsonArray events = root.getAsJsonArray("events");
                if (events == null) return outList;

                for (JsonElement el : events) {
                    JsonObject ev = el.getAsJsonObject();
                    DisasterEvent d = new DisasterEvent();
                    d.id = ev.get("id").getAsString();
                    d.title = ev.has("title") ? ev.get("title").getAsString() : "n/a";
                    JsonArray cats = ev.getAsJsonArray("categories");
                    d.category = (cats != null && cats.size() > 0)
                            ? cats.get(0).getAsJsonObject().get("title").getAsString()
                            : "unknown";

                    JsonArray geoms = ev.getAsJsonArray("geometry");
                    if (geoms != null && geoms.size() > 0) {
                        JsonObject g = geoms.get(geoms.size()-1).getAsJsonObject();
                        JsonArray coords = g.getAsJsonArray("coordinates");
                        // EONET geometry order is [lon, lat]
                        d.longitude = coords.get(0).getAsDouble();
                        d.latitude = coords.get(1).getAsDouble();
                        d.date = g.has("date") ? g.get("date").getAsString() : Instant.now().toString();
                    } else {
                        d.latitude = 0;
                        d.longitude = 0;
                        d.date = Instant.now().toString();
                    }

                    d.source = sourceName();
                    d.url = (ev.has("sources") && ev.getAsJsonArray("sources").size() > 0)
                            ? ev.getAsJsonArray("sources").get(0).getAsJsonObject().get("url").getAsString()
                            : API;
                    d.magnitude = 0.0;
                    outList.add(d);
                }
                return outList;
            });
            return out;
        } catch (Exception ex) {
            // Log concise error and rethrow so Main can show stacktrace if desired
            System.err.println("EONET fetch exception: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            throw ex;
        }
    }
}
