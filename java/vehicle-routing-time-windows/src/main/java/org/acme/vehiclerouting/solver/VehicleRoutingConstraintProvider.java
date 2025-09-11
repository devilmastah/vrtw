package org.acme.vehiclerouting.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import java.lang.invoke.ConstantBootstraps;
import java.util.function.Function;
import java.time.Duration;
import java.time.LocalDateTime;

import org.acme.vehiclerouting.domain.Customer;
import org.acme.vehiclerouting.domain.Vehicle;

public class VehicleRoutingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                serviceFinishedAfterDueTime(factory),
                minimizeTravelTime(factory),
                vehicleIsFixed(factory),
                cheffHasPaneltyLargeOrder(factory),
                noTwoCustomersAtSameDueTime(factory),
                noTwoCustomersAtSameEndTime(factory),
                customerDueTimeNotOverlapping(factory) // Added the new constraint here
                // carAvailableForDaysegmentOfCustomer(factory),
                // vehicleIsNotDoingShit(factory)
        };
    }

    // ************************************************************************
    // Hard constraints
    // ************************************************************************

    protected Constraint serviceFinishedAfterDueTime(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
                .filter(Customer::isServiceFinishedAfterDueTime)
                .penalizeLong(HardSoftLongScore.ONE_HARD, customer -> {
                    long penalty = customer.getServiceFinishedDelayAsPanelty();
                   // System.out.println("Penalty for serviceFinishedAfterDueTime: " + penalty);
                    return penalty;
                })
                .asConstraint("serviceFinishedAfterDueTime");
    }

    





    protected Constraint vehicleIsFixed(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
                .filter(Customer::isFixedAssignment)
                .filter(customer -> !customer.getFixedVehicle().equals(customer.getVehicle()))        
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        Customer::isFixedAssignmentPenalty)
                .asConstraint("vehicleIsFixed");
    }

 

    // ************************************************************************
    // Soft constraints
    // 27-06 the large order panelty was changed from ONE_HARD to ONE_SOFT, to check how this works out
    // ************************************************************************

   protected Constraint minimizeTravelTime(ConstraintFactory factory) {
       return factory.forEach(Vehicle.class)
               .penalizeLong(HardSoftLongScore.ONE_SOFT,
                  vehicle -> {
                    long penalty = vehicle.getTotalDrivingTimeSeconds();
                   // System.out.println("Penalty for minimizeTravelTime: " + penalty);
                    return penalty;
                })
               .asConstraint("minimizeTravelTime");
   }


    protected Constraint cheffHasPaneltyLargeOrder(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
            .filter(customer -> customer.getAmountPizza() > 10)
            .join(Vehicle.class,
                Joiners.equal(Customer::getVehicle, Function.identity()))
            .penalizeLong(HardSoftLongScore.ONE_SOFT,
                (customer, vehicle) -> calculateChefPenalty(customer.getAmountPizza(), vehicle.getCheffLevel()))
            .asConstraint("cheffHasPaneltyLargeOrder");
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

    // New constraint to check if customer.dueTime is not within another customer's dueTime and dueTime + serviceDuration
    protected Constraint customerDueTimeNotOverlapping(ConstraintFactory factory) {
        LocalDateTime minDateTime = LocalDateTime.of(1, 1, 1, 0, 0);
        LocalDateTime maxDateTime = LocalDateTime.of(9999, 12, 31, 23, 59);

        return factory.forEach(Customer.class)
                .join(Customer.class,
                        Joiners.equal(Customer::getVehicle),
                        Joiners.lessThan(customer -> customer.getDueTime() != null ? customer.getDueTime() : maxDateTime,
                                customer -> customer.getDueTime() != null && customer.getServiceDuration() != null ? customer.getDueTime().plus(customer.getServiceDuration()) : minDateTime),
                        Joiners.greaterThan(customer -> customer.getDueTime() != null ? customer.getDueTime() : minDateTime))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        (customer1, customer2) -> {
                            LocalDateTime dueTime1 = customer1.getDueTime() != null ? customer1.getDueTime() : maxDateTime;
                            LocalDateTime dueTime2 = customer2.getDueTime() != null ? customer2.getDueTime() : maxDateTime;
                            LocalDateTime serviceEnd2 = customer2.getDueTime() != null && customer2.getServiceDuration() != null ? customer2.getDueTime().plus(customer2.getServiceDuration()) : minDateTime;
                            long differenceInSeconds = Duration.between(dueTime1, serviceEnd2).abs().getSeconds();
                            return differenceInSeconds * 100L; // Penalty based on time difference
                        })
                .asConstraint("customerDueTimeNotOverlapping");
    }

    // Uncomment and define these methods if needed
    /*
    protected Constraint carAvailableForDaysegmentOfCustomer(ConstraintFactory factory) {
        return factory.forEach(Customer.class)
                .join(Vehicle.class)
                .filter((customer, vehicle) -> !vehicle.getDaySegments().contains(customer.getOrderIsDaySegment()))
                .penalizeLong(HardSoftLongScore.ONE_HARD, (customer, vehicle) -> customer.isOtherMenuPenelty())
                .asConstraint("carAvailableForDaysegmentOfCustomer");
    }
    */

    // New constraint to ensure no two customers can be at the same dueTime
protected Constraint noTwoCustomersAtSameDueTime(ConstraintFactory factory) {
    return factory.forEachUniquePair(Customer.class,
            Joiners.equal(Customer::getVehicle),
            Joiners.equal(Customer::getDueTime))
        .penalizeLong(HardSoftLongScore.ONE_HARD, (customer1, customer2) -> {
            // Penalty can be adjusted as needed
            return 2000000L;
        })
        .asConstraint("noTwoCustomersAtSameDueTime");
}

protected Constraint noTwoCustomersAtSameEndTime(ConstraintFactory factory) {
    return factory.forEachUniquePair(Customer.class,
            Joiners.equal(Customer::getVehicle),
            Joiners.equal(customer -> customer.getDueTime() != null && customer.getServiceDuration() != null ? customer.getDueTime().plus(customer.getServiceDuration()) : null))
        .penalizeLong(HardSoftLongScore.ONE_HARD, (customer1, customer2) -> {
            // Penalty can be adjusted as needed
            return 2000000L;
        })
        .asConstraint("noTwoCustomersAtSameEndTime");
}



}
