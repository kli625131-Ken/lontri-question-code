package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.TempUserCreateDTO;
import com.problem.dto.TempUserUpdateDTO;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/temp-users")
@RequiredArgsConstructor
public class AdminTempUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<?> listTempUsers() {
        return Result.success(adminUserService.listUsers("TEMP"));
    }

    @PostMapping
    public Result<?> createTempUser(@Valid @RequestBody TempUserCreateDTO dto) {
        return Result.success(adminUserService.createTempUser(dto));
    }

    @PutMapping("/{id}")
    public Result<?> updateTempUser(@PathVariable Long id, @RequestBody TempUserUpdateDTO dto) {
        return Result.success(adminUserService.updateTempUser(id, dto));
    }

    @PatchMapping("/{id}/disable")
    public Result<?> disableTempUser(@PathVariable Long id) {
        return Result.success(adminUserService.updateUserStatus(id, 0));
    }
}
