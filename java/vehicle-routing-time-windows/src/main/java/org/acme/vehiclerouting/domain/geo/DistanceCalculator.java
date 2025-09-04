package org.acme.vehiclerouting.domain.geo;

import org.acme.vehiclerouting.domain.Location;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public interface DistanceCalculator {

    // If calling from inside Docker, use the service name+port (change if needed):
    String BASE_URL = "http://ors-app:8081/ors/v2/directions/driving-car";
    String API_KEY  = ""; // "5b3ce3..." or "" if disabled

    // Behavior toggles
    String  ORS_PREFERENCE   = "shortest"; // or "fastest"
    boolean AVOID_HIGHWAYS   = true;       // flip to false if you want
    Integer MAXIMUM_SPEED_KM = 85;         // null to omit

    int CONNECT_TIMEOUT_MS = 1500;
    int SOCKET_TIMEOUT_MS  = 2000;

    ObjectMapper MAPPER = new ObjectMapper();

    default long calculateDistance(Location from, Location to) {
        if (from.equals(to)) return 0L;

        String url = BASE_URL + "?format=geojson" + (API_KEY.isEmpty() ? "" : "&api_key=" + API_KEY);

        String body = buildRequestBody(from, to);

        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

        try (CloseableHttpClient http = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

            HttpResponse resp = http.execute(post);
            int code = resp.getStatusLine().getStatusCode();
            String payload = EntityUtils.toString(resp.getEntity());

            if (code >= 200 && code < 300) {
                Long secs = parseDurationSeconds(payload);
                if (secs != null) return secs;
            } else {
                System.err.println("[DistanceCalculator] ORS HTTP " + code + " payload: " + payload);
            }
        } catch (IOException e) {
            System.err.println("[DistanceCalculator] ORS error: " + e.getMessage());
        }
        // Fallback: simple straight-line estimate (optional: return -1 instead)
        return -1L;
    }

    default Map<Location, Map<Location, Long>> calculateBulkDistance(
            Collection<Location> fromLocations,
            Collection<Location> toLocations) {
        Map<Location, Map<Location, Long>> distanceMatrix = new HashMap<>();
        for (Location from : fromLocations) {
            Map<Location, Long> row = new HashMap<>();
            for (Location to : toLocations) {
                if (!from.equals(to)) {
                    long seconds = calculateDistance(from, to);
                    row.put(to, seconds);
                }
            }
            distanceMatrix.put(from, row);
        }
        return distanceMatrix;
    }

    default void initDistanceMaps(Collection<Location> locations) {
        Map<Location, Map<Location, Long>> dm = calculateBulkDistance(locations, locations);
        locations.forEach(loc -> loc.setDrivingTimeSecondsMap(dm.get(loc)));
    }

    // --- helpers ---

    private static String buildRequestBody(Location from, Location to) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"coordinates\":[[")
          .append(from.getLongitude()).append(',').append(from.getLatitude())
          .append("],[")
          .append(to.getLongitude()).append(',').append(to.getLatitude())
          .append("]]");
        sb.append(",\"preference\":\"").append(ORS_PREFERENCE).append("\"");
        if (MAXIMUM_SPEED_KM != null) {
            sb.append(",\"maximum_speed\":").append(MAXIMUM_SPEED_KM);
        }
        if (AVOID_HIGHWAYS) {
            sb.append(",\"options\":{\"avoid_features\":[\"highways\"]}");
        }
        sb.append('}');
        return sb.toString();
    }

    /** Returns seconds or null. Handles both GeoJSON (features) and routes shapes. */
    private static Long parseDurationSeconds(String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);

            // Shape A: GeoJSON FeatureCollection
            JsonNode features = root.path("features");
            if (features.isArray() && features.size() > 0) {
                JsonNode props = features.get(0).path("properties");
                JsonNode summary = props.path("summary");
                if (summary.isObject()) {
                    JsonNode dur = summary.path("duration");
                    if (dur.isNumber()) return Math.round(dur.asDouble());
                }
                JsonNode segments = props.path("segments");
                if (segments.isArray() && segments.size() > 0) {
                    JsonNode dur = segments.get(0).path("duration");
                    if (dur.isNumber()) return Math.round(dur.asDouble());
                }
            }

            // Shape B: routes[0].summary.duration
            JsonNode routes = root.path("routes");
            if (routes.isArray() && routes.size() > 0) {
                JsonNode summary = routes.get(0).path("summary");
                if (summary.isObject()) {
                    JsonNode dur = summary.path("duration");
                    if (dur.isNumber()) return Math.round(dur.asDouble());
                }
            }
        } catch (Exception e) {
            System.err.println("[DistanceCalculator] JSON parse error: " + e.getMessage());
        }
        return null;
    }
}
