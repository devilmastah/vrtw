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
    String BASE_URL = "http://ors-app:8082/ors/v2/directions/driving-car";
    String API_KEY = ""; // "5b3ce3..." or "" if disabled

    // Behavior toggles
    String ORS_PREFERENCE = "shortest"; // or "fastest"
    Integer MAXIMUM_SPEED_KM = 85;      // null to omit

    int CONNECT_TIMEOUT_MS = 1500;
    int SOCKET_TIMEOUT_MS = 2000;

    ObjectMapper MAPPER = new ObjectMapper();

    long calculateDistance(Location from, Location to, boolean allowHighways);

    default Map<Location, Map<Location, Long>> calculateBulkDistance(
            Collection<Location> fromLocations,
            Collection<Location> toLocations, boolean allowHighways) {
        Map<Location, Map<Location, Long>> distanceMatrix = new HashMap<>();
        for (Location from : fromLocations) {
            Map<Location, Long> row = new HashMap<>();
            for (Location to : toLocations) {
                if (!from.equals(to)) {
                    long seconds = calculateDistance(from, to, allowHighways);
                    row.put(to, seconds);
                }
            }
            distanceMatrix.put(from, row);
        }
        return distanceMatrix;
    }

    default void initDistanceMaps(Collection<Location> locations) {
        // Calculate for "without highways"
        Map<Location, Map<Location, Long>> withoutHighwaysDm = calculateBulkDistance(locations, locations, false);
        locations.forEach(loc -> loc.setDrivingTimeWithoutHighwaysMap(withoutHighwaysDm.get(loc)));

        // Calculate for "with highways"
        Map<Location, Map<Location, Long>> withHighwaysDm = calculateBulkDistance(locations, locations, true);
        locations.forEach(loc -> loc.setDrivingTimeWithHighwaysMap(withHighwaysDm.get(loc)));
    }

    // --- helpers ---

    private static String buildRequestBody(Location from, Location to, boolean allowHighways) {
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
        if (!allowHighways) {
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