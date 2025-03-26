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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface DistanceCalculator {

    String API_KEY = "5b3ce3597851110001cf6248e81220611dab419c9fd80d4f8bfb8e21"; // Replace with your actual API key
    String BASE_URL = "http://localhost/ors/v2/directions/driving-car/";



    default long calculateDistance(Location from, Location to) {

        String requestUrl = BASE_URL + "?"
                + "start=" + from.getLongitude() + "," + from.getLatitude()
                + "&end=" + to.getLongitude() + "," + to.getLatitude();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(requestUrl);
            HttpResponse response = httpClient.execute(request);
            String jsonResponse = EntityUtils.toString(response.getEntity());
            System.out.print(jsonResponse);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            JsonNode routesNode = rootNode.path("routes");
            if (!routesNode.isMissingNode() && routesNode.isArray() && routesNode.has(0)) {
                JsonNode summaryNode = routesNode.get(0).path("summary");
                if (!summaryNode.isMissingNode()) {
                    return summaryNode.path("duration").asLong();
                }
            }
            return -1; // Handle this case as needed
        } catch (IOException e) {
            e.printStackTrace();
            return -1; // Handle exception more gracefully
        }
    }

    default Map<Location, Map<Location, Long>> calculateBulkDistance(
            Collection<Location> fromLocations,
            Collection<Location> toLocations) {
        Map<Location, Map<Location, Long>> distanceMatrix = new HashMap<>();

        for (Location from : fromLocations) {
            Map<Location, Long> distanceMap = new HashMap<>();
            for (Location to : toLocations) {
                if (!from.equals(to)) {
                    long distance = calculateDistance(from, to);
                    distanceMap.put(to, distance);
                }
            }
            distanceMatrix.put(from, distanceMap);
        }

        return distanceMatrix;
    }

    default void initDistanceMaps(Collection<Location> locations) {
        Map<Location, Map<Location, Long>> distanceMatrix = calculateBulkDistance(locations, locations);
        locations.forEach(location -> location.setDrivingTimeSecondsMap(distanceMatrix.get(location)));
    }
}