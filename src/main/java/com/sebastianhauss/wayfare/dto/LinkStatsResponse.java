package com.sebastianhauss.wayfare.dto;

import java.util.List;

public record LinkStatsResponse(
        long totalClicks,
        List<DailyCount> clicksByDay,
        List<Bucket> topReferrers,
        List<Bucket> topCountries,
        List<Bucket> devices) {

    public record DailyCount(String day, long count) {
    }

    public record Bucket(String label, long count) {
    }
}
