package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_maintenance_personnel")
public class OpsMaintenancePersonnel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long visitId;
    private String personName;
    private String phone;
    private String roleName;
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
