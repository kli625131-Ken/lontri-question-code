package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_project_contact")
public class OpsProjectContact {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String positionTitle;
    private String contactName;
    private String contactInfo;
    private String responsibility;
    private String notes;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String rawSnapshot;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
