package com.sebastianhauss.wayfare.repository.projection;

/**
 * Projection for the "clicks per day" time series. {@code day} is a YYYY-MM-DD
 * string so the response is timezone-agnostic on the wire.
 */
public interface DailyCount {
    String getDay();

    long getCount();
}
