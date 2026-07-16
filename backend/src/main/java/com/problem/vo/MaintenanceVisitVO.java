package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MaintenanceVisitVO {
    private Long id;
    private Long projectId;
    private String customerName;
    private String projectGroup;
    private String projectName;
    private String projectCode;
    private String visitNo;
    private String visitTitle;
    private String servicePeriod;
    private Integer serviceYear;
    private Integer serviceQuarter;
    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private String status;
    private String summary;
    private String conclusion;
    private String sourceFilePath;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String sourceHash;
    private Integer assignmentCount;
    private Integer personnelCount;
    private Integer findingCount;
    private Integer unresolvedFindingCount;
    private Integer quoteItemCount;
    private BigDecimal quoteTotalAmount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MaintenanceAssignmentVO> assignments;
    private List<MaintenancePersonnelVO> personnel;
    private List<MaintenanceFindingVO> findings;
    private List<MaintenanceQuoteItemVO> quoteItems;
    private List<MaintenanceSourceFileVO> sourceFiles;
}
