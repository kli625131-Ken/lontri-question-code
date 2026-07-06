package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerName;
    private String projectGroup;
    private String projectName;
    private String projectCode;
    private String parentProjectCode;
    private String projectLevel;
    private String description;
    private Integer reminderEnabled;
    private Integer remindAfterDays;

    @TableField("is_active")
    private Integer isActive;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
