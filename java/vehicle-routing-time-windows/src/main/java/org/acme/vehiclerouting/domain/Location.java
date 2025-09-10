package org.acme.vehiclerouting.domain;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Location {

    private double latitude;
    private double longitude;

    @JsonIgnore
    private Map<Location, Long> drivingTimeWithoutHighwaysMap;
    @JsonIgnore
    private Map<Location, Long> drivingTimeWithHighwaysMap;

    @JsonCreator
    public Location(@JsonProperty("latitude") double latitude, @JsonProperty("longitude") double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /**
     * Set the driving time map (in seconds) for routes avoiding highways.
     *
     * @param drivingTimeWithoutHighwaysMap a map containing driving time from here to other locations
     */
    public void setDrivingTimeWithoutHighwaysMap(Map<Location, Long> drivingTimeWithoutHighwaysMap) {
        this.drivingTimeWithoutHighwaysMap = drivingTimeWithoutHighwaysMap;
    }

    /**
     * Set the driving time map (in seconds) for routes allowing highways.
     *
     * @param drivingTimeWithHighwaysMap a map containing driving time from here to other locations
     */
    public void setDrivingTimeWithHighwaysMap(Map<Location, Long> drivingTimeWithHighwaysMap) {
        this.drivingTimeWithHighwaysMap = drivingTimeWithHighwaysMap;
    }

    /**
     * Driving time to the given location in seconds, based on highway preference.
     *
     * @param location      other location
     * @param allowHighways whether to use the travel time matrix that allows highways
     * @return driving time in seconds
     */
    public long getDrivingTimeTo(Location location, boolean allowHighways) {
        if (allowHighways) {
            return drivingTimeWithHighwaysMap.get(location);
        } else {
            return drivingTimeWithoutHighwaysMap.get(location);
        }
    }
}