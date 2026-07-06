package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_project")
public class UserProject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
