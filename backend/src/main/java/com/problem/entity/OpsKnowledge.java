package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_knowledge")
public class OpsKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private Long projectId;
    private String sourceType;
    private String sourceName;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String title;
    private String faultCode;
    private String symptomSummary;
    private String causeSummary;
    private String solutionSummary;
    private String preventionSummary;
    private String tags;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
