package org.acme.vehiclerouting.domain.geo;

import org.acme.vehiclerouting.domain.Location;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HaversineDistanceCalculator implements DistanceCalculator {

 
    String BASE_URL = "http://localhost:8081/ors/v2/directions/driving-car/";

    private static final int EARTH_RADIUS_IN_KM = 6371;
    private static final int TWICE_EARTH_RADIUS_IN_KM = 2 * EARTH_RADIUS_IN_KM;
    public static final int AVERAGE_SPEED_KMPH = 65;

    public static long kilometersToDrivingSeconds(double kilometers) {
        return Math.round(kilometers / AVERAGE_SPEED_KMPH * 3600);
    }

    @Override
    public long calculateDistance(Location from, Location to) {

        if (from.equals(to)) {
            return 0L;
        }
 

        String requestUrl = BASE_URL + "?"
                + "start=" + from.getLongitude() + "," + from.getLatitude()
                + "&maximum_speed=85" 
                + "&end=" + to.getLongitude() + "," + to.getLatitude();
                //System.out.println("URL: " + requestUrl);            
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(requestUrl);
            HttpResponse response = httpClient.execute(request);
            String jsonResponse = EntityUtils.toString(response.getEntity());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            JsonNode routesNode = rootNode.path("features");
            if (!routesNode.isMissingNode() && routesNode.isArray() && routesNode.has(0)) {
                JsonNode propertiesNode = routesNode.get(0).path("properties");
                if (!propertiesNode.isMissingNode()) {
                    JsonNode summaryNode = propertiesNode.path("segments");
                    if (!summaryNode.isMissingNode()) {
                        return summaryNode.get(0).path("duration").asLong();
                    }
                }
            }
            return this.calculateDistanceBup(locationToCartesian(from), locationToCartesian(to));
        } catch (IOException e) {
            e.printStackTrace();
              return this.calculateDistanceBup(locationToCartesian(from), locationToCartesian(to));
        }
    }

    private long calculateDistanceBup(CartesianCoordinate from, CartesianCoordinate to) {
        if (from.equals(to)) {
            return 0L;
        }

        double dX = from.x - to.x;
        double dY = from.y - to.y;
        double dZ = from.z - to.z;
        double r = Math.sqrt((dX * dX) + (dY * dY) + (dZ * dZ));
        return kilometersToDrivingSeconds(TWICE_EARTH_RADIUS_IN_KM * Math.asin(r));
    }

    private CartesianCoordinate locationToCartesian(Location location) {
        double latitudeInRads = Math.toRadians(location.getLatitude());
        double longitudeInRads = Math.toRadians(location.getLongitude());
        // Cartesian coordinates, normalized for a sphere of diameter 1.0
        double cartesianX = 0.5 * Math.cos(latitudeInRads) * Math.sin(longitudeInRads);
        double cartesianY = 0.5 * Math.cos(latitudeInRads) * Math.cos(longitudeInRads);
        double cartesianZ = 0.5 * Math.sin(latitudeInRads);
        return new CartesianCoordinate(cartesianX, cartesianY, cartesianZ);
    }

    private record CartesianCoordinate(double x, double y, double z) {

    }
}
