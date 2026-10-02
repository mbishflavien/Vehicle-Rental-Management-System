package com.vrms.dto;

import com.vrms.model.AuditLog;

import java.util.List;

/**
 * Numbers for the staff dashboard KPI cards. Trends compare the last 30 days to the 30 days before;
 * a null trend means there was nothing to compare against.
 */
public record DashboardStats(
        double revenueLast30Days,
        Double revenueTrendPercent,
        long activeContracts,
        long newContractsLast30Days,
        double utilizationPercent,
        long rentedVehicles,
        long availableVehicles,
        long totalVehicles,
        long maintenanceVehicles,
        long pendingContracts,
        long totalCustomers,
        List<AuditLog> recentActivity) {
}
