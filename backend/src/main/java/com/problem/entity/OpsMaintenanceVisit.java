package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_maintenance_visit")
public class OpsMaintenanceVisit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
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
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
