package org.acme.vehiclerouting.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.NextElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;

import org.acme.vehiclerouting.solver.ArrivalTimeUpdatingVariableListener;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@JsonIdentityInfo(scope = Customer.class, generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@PlanningEntity
public class Customer {

    @PlanningId
    private String id;

    private String name;
    private Location location;
    private LocalDateTime readyTime;
    private LocalDateTime dueTime;
    private Duration serviceDuration;

    private Vehicle vehicle;
    private Vehicle fixedVehicle;
    private int amountPizza;

    private Customer previousCustomer;

    private Customer nextCustomer;
    private long isPenalizedCheffPanelty;
    private LocalDateTime arrivalTime;
    private int cheffLevelRequired;
    private int orderIsDaySegment;
    // NEW: per-customer routing preference (default false = avoid highways)
    private boolean allowHighways = false;


    public Customer() {
    }

    public Customer(String id, String name, Location location, LocalDateTime readyTime, LocalDateTime dueTime,
                    Duration serviceDuration, Vehicle fixedVehicle, int amountPizza, long isPenalizedCheffPanelty, int orderIsDaySegment) {
        this.id = id;
        this.name = name;
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceDuration = serviceDuration;
        this.location = location;
        this.fixedVehicle = fixedVehicle;
        this.amountPizza = amountPizza;
        this.isPenalizedCheffPanelty = isPenalizedCheffPanelty;
        this.orderIsDaySegment = orderIsDaySegment;
    }


    public boolean isAllowHighways() {
        return allowHighways;
    }

    public void setAllowHighways(boolean allowHighways) {
        this.allowHighways = allowHighways;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Vehicle getFixedVehicle() {
        return fixedVehicle;
    }

    public void setFixedVehicle(Vehicle fixedVehicle) {
        this.fixedVehicle = fixedVehicle;
    }

    public int getAmountPizza() {
        return amountPizza;
    }


    public int getCheffLevelRequired() {
        return cheffLevelRequired;
    }

    public void setCheffLevelRequired(int cheffLevelRequired) {
        this.cheffLevelRequired = cheffLevelRequired;
    }


    public int getOrderIsDaySegment() {
        return orderIsDaySegment;
    }

    public void setOrderIsDaySegment(int orderIsDaySegment) {
        this.orderIsDaySegment = orderIsDaySegment;
    }

    public void setAmountPizza(int amountPizza) {
        this.amountPizza = amountPizza;
    }

    public LocalDateTime getReadyTime() {
        return readyTime;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public Duration getServiceDuration() {
        return serviceDuration;
    }

    @JsonIdentityReference(alwaysAsId = true)
    @InverseRelationShadowVariable(sourceVariableName = "customers")
    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @JsonIdentityReference(alwaysAsId = true)
    @PreviousElementShadowVariable(sourceVariableName = "customers")
    public Customer getPreviousCustomer() {
        return previousCustomer;
    }

    public void setPreviousCustomer(Customer previousCustomer) {
        this.previousCustomer = previousCustomer;
    }

    @JsonIdentityReference(alwaysAsId = true)
    @NextElementShadowVariable(sourceVariableName = "customers")
    public Customer getNextCustomer() {
        return nextCustomer;
    }

    public void setNextCustomer(Customer nextCustomer) {
        this.nextCustomer = nextCustomer;
    }

    @ShadowVariable(variableListenerClass = ArrivalTimeUpdatingVariableListener.class, sourceVariableName = "vehicle")
    @ShadowVariable(variableListenerClass = ArrivalTimeUpdatingVariableListener.class, sourceVariableName = "previousCustomer")
    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // ************************************************************************
    // Complex methods
    // ************************************************************************

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public LocalDateTime getDepartureTime() {
        if (arrivalTime == null) {
            return null;
        }
        return getStartServiceTime().plus(serviceDuration);
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public LocalDateTime getStartServiceTime() {
        if (arrivalTime == null) {
            return null;
        }
        return arrivalTime.isBefore(readyTime) ? readyTime : arrivalTime;
    }

    @JsonIgnore
    public boolean isFixedAssignment() {
        return fixedVehicle != null;
    }

    @JsonIgnore
    public long isFixedAssignmentPenalty() {

        return 200000000;
    }

    @JsonIgnore
    public long isOtherMenuPenelty() {

        return 300000000;
    }

    @JsonIgnore
    public long isPenalizedCheffPanelty() {
        return 150000;
    }

    @JsonIgnore
    public boolean isServiceFinishedAfterDueTime() {
        return arrivalTime != null
                && arrivalTime.plus(serviceDuration).isAfter(dueTime);
    }


    @JsonIgnore
    public long getServiceFinishedDelayAsPanelty() {
        if (arrivalTime == null) {
            return 0;
        }
        return (Duration.between(dueTime, arrivalTime.plus(serviceDuration)).toMinutes() * Duration.between(dueTime, arrivalTime.plus(serviceDuration)).toMinutes()) * 5L;
    }


    @JsonIgnore
    public long getServiceFinishedDelayInMinutes() {
        if (arrivalTime == null) {
            return 0;
        }
        return Duration.between(dueTime, arrivalTime.plus(serviceDuration)).toMinutes();
    }

    @JsonIgnore
    public long getDrivingTimeSecondsFromPreviousStandstill() {
        if (vehicle == null) {
            throw new IllegalStateException(
                    "This method must not be called when the shadow variables are not initialized yet.");
        }
        boolean allowHighwaysDecision;
        if (previousCustomer == null) { // Departs from depot
            // Depot is assumed to have allowHighways = false.
            // Decision is based on the current customer's preference.
            allowHighwaysDecision = this.isAllowHighways();
            return vehicle.getDepot().getLocation().getDrivingTimeTo(location, allowHighwaysDecision);
        } else { // Departs from another customer
            allowHighwaysDecision = this.isAllowHighways() || previousCustomer.isAllowHighways();
            return previousCustomer.getLocation().getDrivingTimeTo(location, allowHighwaysDecision);
        }
    }

    // Required by the web UI even before the solution has been initialized.
    @JsonProperty(value = "drivingTimeSecondsFromPreviousStandstill", access = JsonProperty.Access.READ_ONLY)
    public Long getDrivingTimeSecondsFromPreviousStandstillOrNull() {
        if (vehicle == null) {
            return null;
        }
        return getDrivingTimeSecondsFromPreviousStandstill();
    }

    @Override
    public String toString() {
        return "Customer{" +
                "id=" + id +
                '}';
    }
}