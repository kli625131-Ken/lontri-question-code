package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.AdminUserCreateDTO;
import com.problem.dto.AdminUserUpdateDTO;
import com.problem.dto.ProjectAuthorizationDTO;
import com.problem.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<?> listUsers() {
        return Result.success(adminUserService.listUsers(null));
    }

    @PostMapping
    public Result<?> createUser(@Valid @RequestBody AdminUserCreateDTO dto) {
        return Result.success(adminUserService.createUser(dto));
    }

    @PutMapping("/{id}")
    public Result<?> updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateDTO dto) {
        return Result.success(adminUserService.updateUser(id, dto));
    }

    @PatchMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.success(adminUserService.updateUserStatus(id, status));
    }

    @PostMapping("/{id}/reset-password")
    public Result<?> resetPassword(@PathVariable Long id) {
        return Result.success(adminUserService.resetPassword(id));
    }

    @GetMapping("/{id}/projects")
    public Result<?> listProjectIds(@PathVariable Long id) {
        return Result.success(adminUserService.listProjectIds(id));
    }

    @PutMapping("/{id}/projects")
    public Result<?> saveProjectIds(@PathVariable Long id, @RequestBody ProjectAuthorizationDTO dto) {
        return Result.success(adminUserService.saveProjectIds(id, dto));
    }
}
