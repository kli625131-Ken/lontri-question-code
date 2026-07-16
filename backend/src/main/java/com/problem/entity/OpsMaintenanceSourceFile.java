package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_maintenance_source_file")
public class OpsMaintenanceSourceFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long visitId;
    private String projectName;
    private String fileType;
    private String fileName;
    private String filePath;
    private String zipEntryPath;
    private String importStatus;
    private String message;
    private String sourceHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
