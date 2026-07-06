package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class IssueRecordVO {
    private Long id;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private String content;
    private String operatorName;
    private LocalDateTime operateTime;
}
