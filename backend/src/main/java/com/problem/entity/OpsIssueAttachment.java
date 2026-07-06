package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_issue_attachment")
public class OpsIssueAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private Long uploadedBy;
    private LocalDateTime createdAt;

    @TableField("deleted_flag")
    @TableLogic(value = "0", delval = "1")
    private Integer deletedFlag;
}
