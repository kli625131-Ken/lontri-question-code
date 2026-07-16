package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MaintenanceOverviewVO {
    private long totalVisits;
    private long plannedVisits;
    private long inProgressVisits;
    private long closedVisits;
    private long totalFindings;
    private long unresolvedFindings;
    private BigDecimal quoteTotalAmount;
}
