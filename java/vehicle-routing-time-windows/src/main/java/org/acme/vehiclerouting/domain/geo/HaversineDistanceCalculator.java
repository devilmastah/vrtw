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

public class HaversineDistanceCalculator implements DistanceCalculator {

    // --- ORS endpoint in your docker-compose network ---
    // If your ORS enforces an API key, set API_KEY accordingly; otherwise leave empty.
    private static final String ORS_BASE = "http://ors-app:8082/ors/v2/directions/driving-car";
    private static final String API_KEY = ""; // e.g. "5b3c...e21" or "" if disabled

    // Behaviour switches (keep method signature unchanged)
    private static final String ORS_PREFERENCE = "fastest"; // "shortest" or "fastest"
    private static final Integer MAXIMUM_SPEED = 85;         // km/h; null to omit

    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int SOCKET_TIMEOUT_MS = 2000;

    private static final int EARTH_RADIUS_IN_KM = 6371;
    private static final int TWICE_EARTH_RADIUS_IN_KM = 2 * EARTH_RADIUS_IN_KM;
    public static final int AVERAGE_SPEED_KMPH = 65;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static long kilometersToDrivingSeconds(double kilometers) {
        return Math.round(kilometers / AVERAGE_SPEED_KMPH * 3600);
    }

    @Override
    public long calculateDistance(Location from, Location to, boolean allowHighways) {
        if (from.equals(to)) return 0L;

        String url = ORS_BASE + "?format=geojson" + (API_KEY.isEmpty() ? "" : "&api_key=" + API_KEY);

        // Build request body
        String jsonBody = buildRequest(from, to, allowHighways);

        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            HttpResponse response = httpClient.execute(post);
            int code = response.getStatusLine().getStatusCode();
            String payload = EntityUtils.toString(response.getEntity());

            if (code >= 200 && code < 300) {
                Long seconds = parseDurationSeconds(payload);
                if (seconds != null) return seconds;
            } else {
                System.err.println("[DistanceCalc] ORS HTTP " + code + " payload: " + payload);
            }

            // Fallback if parsing failed
            return this.calculateDistanceBup(locationToCartesian(from), locationToCartesian(to));

        } catch (IOException e) {
            System.err.println("[DistanceCalc] Error calling ORS: " + e.getMessage());
            return this.calculateDistanceBup(locationToCartesian(from), locationToCartesian(to));
        }
    }

    private static String buildRequest(Location from, Location to, boolean allowHighways) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');

        // coordinates
        sb.append("\"coordinates\":[[")
                .append(from.getLongitude()).append(',').append(from.getLatitude())
                .append("],[")
                .append(to.getLongitude()).append(',').append(to.getLatitude())
                .append("]");

        sb.append("]");

        // preference
        sb.append(",\"preference\":\"").append(ORS_PREFERENCE).append('"');

        // maximum_speed (optional top-level in v2)
        if (MAXIMUM_SPEED != null) {
            sb.append(",\"maximum_speed\":").append(MAXIMUM_SPEED);
        }

        // options.avoid_features
        if (!allowHighways) {
           // sb.append(",\"options\":{\"avoid_features\":[\"highways\"]}");
        }

        sb.append('}');

        System.out.println("[DistanceCalc] ORS request body: " + sb.toString());


        return sb.toString();
    }

    /** Returns seconds or null if unexpected. Handles both GeoJSON and routes shapes. */
    private static Long parseDurationSeconds(String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);

            // Shape A: GeoJSON FeatureCollection (features[0].properties.summary.duration)
            JsonNode features = root.path("features");
            if (features.isArray() && features.size() > 0) {
                JsonNode props = features.get(0).path("properties");
                // some ORS builds: properties.summary, some: properties.segments[0]
                JsonNode summary = props.path("summary");
                if (!summary.isMissingNode()) {
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
                if (!summary.isMissingNode()) {
                    JsonNode dur = summary.path("duration");
                    if (dur.isNumber()) return Math.round(dur.asDouble());
                }
            }

        } catch (Exception e) {
            System.err.println("[DistanceCalc] JSON parse error: " + e.getMessage());
        }
        return null;
    }

    // --- Existing haversine-ish fallback (unchanged) ---
    private long calculateDistanceBup(CartesianCoordinate from, CartesianCoordinate to) {
        if (from.equals(to)) return 0L;
        double dX = from.x - to.x;
        double dY = from.y - to.y;
        double dZ = from.z - to.z;
        double r = Math.sqrt((dX * dX) + (dY * dY) + (dZ * dZ));
        return kilometersToDrivingSeconds(TWICE_EARTH_RADIUS_IN_KM * Math.asin(r));
    }

    private CartesianCoordinate locationToCartesian(Location location) {
        double latRad = Math.toRadians(location.getLatitude());
        double lonRad = Math.toRadians(location.getLongitude());
        double x = 0.5 * Math.cos(latRad) * Math.sin(lonRad);
        double y = 0.5 * Math.cos(latRad) * Math.cos(lonRad);
        double z = 0.5 * Math.sin(latRad);
        return new CartesianCoordinate(x, y, z);
    }

    private record CartesianCoordinate(double x, double y, double z) {
    }
}