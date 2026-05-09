package com.zaplink.dto;

public record SystemReportsResponse(
        UserStats userStats,
        LinkStats linkStats,
        ClickStats clickStats
) {
    public record UserStats(
            Long totalUsers,
            Long activeUsers,
            Long bannedUsers,
            Long newUsersLast30Days
    ) {}

    public record LinkStats(
            Long totalLinks,
            Long activeLinks,
            Long expiredLinks,
            Long disabledLinks,
            Long deletedLinks,
            Long orphanedLinks,
            Long newLinksLast30Days
    ) {}

    public record ClickStats(
            Long totalClicks,
            Long clicksLast30Days
    ) {}
}
