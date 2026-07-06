package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_issue_record")
public class OpsIssueRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private String content;
    private String operatorName;
    private LocalDateTime operateTime;

    @TableLogic
    private Integer deleted;
}
