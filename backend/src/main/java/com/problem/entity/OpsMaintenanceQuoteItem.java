package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ops_maintenance_quote_item")
public class OpsMaintenanceQuoteItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long visitId;
    private String areaName;
    private String itemName;
    private BigDecimal quantity;
    private String unitName;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String notes;
    private String sourceFilePath;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String sourceHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
