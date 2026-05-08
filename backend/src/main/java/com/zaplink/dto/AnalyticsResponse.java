package com.zaplink.dto;

import java.util.List;

public record AnalyticsResponse(
        Long linkId,
        String shortCode,
        Long totalClicks,
        Long last30DaysClicks,
        List<DailyClickCount> dailyClicks
) {}
