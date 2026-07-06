package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.RoleUpdateDTO;
import com.problem.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<?> listRoles() {
        return Result.success(adminUserService.listRoles());
    }

    @PutMapping("/{id}")
    public Result<?> updateRole(@PathVariable Long id, @RequestBody RoleUpdateDTO dto) {
        return Result.success(adminUserService.updateRole(id, dto));
    }
}
