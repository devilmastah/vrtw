package org.acme.vehiclerouting.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import org.acme.vehiclerouting.domain.Customer;
import org.acme.vehiclerouting.domain.Vehicle;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

public class VehicleRoutingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                // --- HARD CONSTRAINTS (Unbreakable) ---
                vehicleIsFixed(factory),
                noTwoCustomersAtSameDueTime(factory),
                noTwoCustomersAtSameEndTime(factory),
                customerDueTimeNotOverlapping(factory),

                // --- MEDIUM CONSTRAINTS (High Priority Problems) ---
                serviceFinishedAfterAcceptableWindow(factory),

                // --- SOFT CONSTRAINTS (Optimizations) ---
                minimizeTravelTime(factory),
                cheffHasPaneltyLargeOrder(factory),
                serviceFinishedAfterDueTimeSoft(factory) // Optional: small penalty for any lateness
        };
    }

    // ************************************************************************
    // Hard constraints
    // ************************************************************************

    protected Constraint vehicleIsFixed(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
                .filter(Customer::isFixedAssignment)
                .filter(customer -> !customer.getFixedVehicle().equals(customer.getVehicle()))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, // FIXED
                        Customer::isFixedAssignmentPenalty)
                .asConstraint("vehicleIsFixed");
    }

    protected Constraint noTwoCustomersAtSameDueTime(ConstraintFactory factory) {
        return factory.forEachUniquePair(Customer.class,
                Joiners.equal(Customer::getVehicle),
                Joiners.equal(Customer::getDueTime))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, (customer1, customer2) -> 2000000L) // FIXED
                .asConstraint("noTwoCustomersAtSameDueTime");
    }

    protected Constraint noTwoCustomersAtSameEndTime(ConstraintFactory factory) {
        return factory.forEachUniquePair(Customer.class,
                        Joiners.equal(Customer::getVehicle),
                        Joiners.equal(customer -> customer.getDueTime() != null && customer.getServiceDuration() != null
                                ? customer.getDueTime().plus(customer.getServiceDuration())
                                : null))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, (customer1, customer2) -> 2000000L) // FIXED
                .asConstraint("noTwoCustomersAtSameEndTime");
    }

    protected Constraint customerDueTimeNotOverlapping(ConstraintFactory factory) {
        LocalDateTime minDateTime = LocalDateTime.of(1, 1, 1, 0, 0);
        LocalDateTime maxDateTime = LocalDateTime.of(9999, 12, 31, 23, 59);

        return factory.forEach(Customer.class)
                .join(Customer.class,
                        Joiners.equal(Customer::getVehicle),
                        Joiners.lessThan(customer -> customer.getDueTime() != null ? customer.getDueTime() : maxDateTime,
                                customer -> customer.getDueTime() != null && customer.getServiceDuration() != null
                                        ? customer.getDueTime().plus(customer.getServiceDuration())
                                        : minDateTime),
                        Joiners.greaterThan(customer -> customer.getDueTime() != null ? customer.getDueTime() : minDateTime))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, // FIXED
                        (customer1, customer2) -> {
                            LocalDateTime dueTime1 = customer1.getDueTime() != null ? customer1.getDueTime() : maxDateTime;
                            LocalDateTime serviceEnd2 = customer2.getDueTime() != null && customer2.getServiceDuration() != null
                                    ? customer2.getDueTime().plus(customer2.getServiceDuration())
                                    : minDateTime;
                            long differenceInSeconds = Duration.between(dueTime1, serviceEnd2).abs().getSeconds();
                            return differenceInSeconds * 100L; // Penalty based on time difference
                        })
                .asConstraint("customerDueTimeNotOverlapping");
    }

    // ************************************************************************
    // Medium constraints
    // ************************************************************************
    protected Constraint serviceFinishedAfterAcceptableWindow(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
                .filter(Customer::isServiceFinishedAfterAcceptableDueTime)
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM, // FIXED
                        customer -> customer.getUnacceptableDelayInMinutes() * customer.getUnacceptableDelayInMinutes())
                .asConstraint("serviceFinishedAfterAcceptableWindow");
    }

    // ************************************************************************
    // Soft constraints
    // ************************************************************************
    protected Constraint minimizeTravelTime(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, // FIXED
                        Vehicle::getTotalDrivingTimeSeconds)
                .asConstraint("minimizeTravelTime");
    }

    protected Constraint cheffHasPaneltyLargeOrder(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
                .filter(customer -> customer.getAmountPizza() > 10)
                .join(Vehicle.class,
                        Joiners.equal(Customer::getVehicle, Function.identity()))
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, // FIXED
                        (customer, vehicle) -> calculateChefPenalty(customer.getAmountPizza(), vehicle.getCheffLevel()))
                .asConstraint("cheffHasPaneltyLargeOrder");
    }

    // Optional but recommended: add a small soft penalty for ANY lateness
    protected Constraint serviceFinishedAfterDueTimeSoft(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
                .filter(Customer::isServiceFinishedAfterDueTime)
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, // FIXED
                        Customer::getServiceFinishedDelayInMinutes)
                .asConstraint("serviceFinishedAfterDueTimeSoft");
    }

    private long calculateChefPenalty(int pizzas, int chefLevel) {
        int limit = 0;
        switch (chefLevel) {
            case 1:
                limit = 10;
                break;
            case 2:
                limit = 20;
                break;
            case 3:
                return 0; // No limit for level 3 chefs
        }

        if (pizzas <= limit) {
            return 0;
        }

        return (pizzas - limit) * 10L; // Penalty calculation
    }
}