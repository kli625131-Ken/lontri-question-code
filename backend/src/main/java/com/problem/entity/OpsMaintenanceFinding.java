package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_maintenance_finding")
public class OpsMaintenanceFinding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long visitId;
    private String floorName;
    private String areaName;
    private String issueDescription;
    private String handlingResult;
    private String completionStatus;
    private String causeAnalysis;
    private String followUpAction;
    private Integer quoteRequired;
    private Integer knowledgeIncluded;
    private LocalDateTime foundAt;
    private String sourceFilePath;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String sourceHash;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
