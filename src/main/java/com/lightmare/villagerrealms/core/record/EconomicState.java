package com.lightmare.villagerrealms.core.record;

import java.util.List;

public record EconomicState(
        long gold,
        List<Debt> debts,
        List<PropertyRef> ownedProperty
) {
    public static final EconomicState ZERO = new EconomicState(0, List.of(), List.of());

    public EconomicState {
        if (debts == null) throw new IllegalArgumentException("debts required");
        if (ownedProperty == null) throw new IllegalArgumentException("ownedProperty required");
        debts = List.copyOf(debts);
        ownedProperty = List.copyOf(ownedProperty);
    }
}
