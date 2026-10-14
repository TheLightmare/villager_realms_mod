package com.lightmare.villagerrealms.core.birth;

/** {@code ScheduledEvent.kind} values the birth system uses. */
public final class ScheduleKinds {

    /** subject = unborn child uuid; data: mother, father (uuids). */
    public static final String BIRTH = "BIRTH";
    /** subject = child uuid. */
    public static final String AGE_UP = "AGE_UP";

    public static final String DATA_MOTHER = "mother";
    public static final String DATA_FATHER = "father";

    private ScheduleKinds() {}
}
