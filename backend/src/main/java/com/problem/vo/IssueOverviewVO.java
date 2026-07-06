package com.problem.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IssueOverviewVO {
    private long totalCount;
    private long openCount;
    private long inProgressCount;
    private long closedCount;
    private long overdueCount;
    private long pendingReviewCount;
}
