package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_maintenance_attachment")
public class OpsMaintenanceAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long findingId;
    private Long visitId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private Long uploadedBy;
    private LocalDateTime createdAt;

    @TableLogic(value = "0", delval = "1")
    private Integer deletedFlag;
}
