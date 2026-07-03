package com.ernoxin.bourseazmaapi.dto.admin;

public record AdminDashboardStatsResponse(
        long totalUsers,
        long onlineUsers,
        long newUsersToday,
        long totalOrders,
        long totalTrades,
        long openTickets
) {
}
