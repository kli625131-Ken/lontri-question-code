package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_project_warranty")
public class OpsProjectWarranty {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String contractType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String serviceScope;
    private LocalDateTime contractSignedAt;
    private LocalDateTime acceptanceAt;
    private String warrantyTerm;
    private LocalDateTime expireAt;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String notes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
