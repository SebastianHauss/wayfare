package com.sebastianhauss.wayfare.repository.projection;

/**
 * Projection for a grouped "how many clicks per bucket" query (e.g. per country,
 * per referrer, per device). The getter names must match the SQL column aliases.
 */
public interface LabelCount {
    String getLabel();

    long getCount();
}
