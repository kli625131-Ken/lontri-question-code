package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_import_batch")
public class OpsImportBatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalFileName;
    private String batchStatus;
    private Integer totalRows;
    private Integer reviewRows;
    private Integer committedRows;
    private Integer skippedRows;
    private String summaryJson;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
