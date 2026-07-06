package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_import_row_review")
public class OpsImportRowReview {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String sheetName;

    @TableField("row_no")
    private Integer rowNumber;

    private String rowType;
    private String reviewStatus;
    private String reviewMessage;
    private String commitStatus;
    private String normalizedData;
    private String rawData;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
