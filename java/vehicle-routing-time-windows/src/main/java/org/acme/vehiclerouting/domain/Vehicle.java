package org.acme.vehiclerouting.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.annotation.JsonIgnore;

@JsonIdentityInfo(scope = Vehicle.class, generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@PlanningEntity
public class Vehicle {

    @PlanningId
    private String id;
    @JsonIdentityReference
    private Depot depot;
    
    @JsonIdentityReference(alwaysAsId = true)
    @PlanningListVariable
    private List<Customer> customers;

    private LocalDateTime departureTime;
    private long isPenalizedCheffPanelty;
    private int cheffLevel;
    private float serviceDurationMultiplier;
    private List<Integer> daySegments;

    public Vehicle() {
    }

    public Vehicle(String id, Depot depot, LocalDateTime departureTime, int cheffPanelty, float serviceDurationMultiplier, List<Integer> daySegments) {
        this.id = id;
        this.depot = depot;
        this.customers = new ArrayList<>();
        this.departureTime = departureTime;
        this.isPenalizedCheffPanelty = 100L;
        this.serviceDurationMultiplier = serviceDurationMultiplier;
        this.daySegments = daySegments;

    }

    public String getId() {
        return id;
    }

   

    public void setId(String id) {
        this.id = id;
    }

    public Depot getDepot() {
        return depot;
    }

    public void setDepot(Depot depot) {
        this.depot = depot;
    }

    public int getCheffLevel()
    {
        return cheffLevel;
    }


    public void setCheffLevel(int cheffLevel)
    {
        this.cheffLevel = cheffLevel;
    }





    public float getServiceDurationMultiplier()
    {
        return serviceDurationMultiplier;
    }


    public void setServiceTimeMultiplier(float serviceDurationMultiplier)
    {
        this.serviceDurationMultiplier = serviceDurationMultiplier;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }


    public List<Integer> getDaySegments() {
        return daySegments;
    }

    public void setDaySegments(ArrayList daySegments) {
        this.daySegments = daySegments;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    // ************************************************************************
    // Complex methods
    // ************************************************************************

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public long getTotalDrivingTimeSeconds() {
        if (customers.isEmpty()) {
            return 0;
        }

        long totalDistance = 0;
        Location previousLocation = depot.getLocation();

        for (Customer customer : customers) {
            totalDistance += previousLocation.getDrivingTimeTo(customer.getLocation());
            previousLocation = customer.getLocation();
        }
        totalDistance += previousLocation.getDrivingTimeTo(depot.getLocation());

        return totalDistance;
    }

     @JsonIgnore
     public boolean vehicleIsUnused() {
        if(getTotalDrivingTimeSeconds() == 0)
        {
            return true;
        }
        return false;
     }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                '}';
    }
}
