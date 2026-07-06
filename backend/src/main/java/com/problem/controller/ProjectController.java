package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.ProjectSaveDTO;
import com.problem.dto.ProjectWarrantySaveDTO;
import com.problem.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public Result<?> listProjects() {
        return Result.success(projectService.listProjects());
    }

    @PostMapping
    public Result<?> createProject(@Valid @RequestBody ProjectSaveDTO dto) {
        return Result.success(projectService.createProject(dto));
    }

    @PutMapping("/{id}")
    public Result<?> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectSaveDTO dto) {
        return Result.success(projectService.updateProject(id, dto));
    }

    @PatchMapping("/{id}/disable")
    public Result<?> disableProject(@PathVariable Long id) {
        return Result.success(projectService.updateProjectStatus(id, 0));
    }

    @PatchMapping("/{id}/enable")
    public Result<?> enableProject(@PathVariable Long id) {
        return Result.success(projectService.updateProjectStatus(id, 1));
    }

    @GetMapping("/{id}/contacts")
    public Result<?> listContacts(@PathVariable Long id) {
        return Result.success(projectService.listContacts(id));
    }

    @GetMapping("/{id}/warranty")
    public Result<?> listWarranty(@PathVariable Long id) {
        return Result.success(projectService.listWarranties(id));
    }

    @PostMapping("/{id}/warranty/upload")
    public Result<?> uploadWarrantyFile(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.success(projectService.uploadWarrantyFile(id, file));
    }

    @PostMapping("/{id}/warranty")
    public Result<?> createWarranty(@PathVariable Long id, @RequestBody ProjectWarrantySaveDTO dto) {
        return Result.success(projectService.createWarranty(id, dto));
    }

    @PutMapping("/{id}/warranty/{warrantyId}")
    public Result<?> updateWarranty(@PathVariable Long id, @PathVariable Long warrantyId, @RequestBody ProjectWarrantySaveDTO dto) {
        return Result.success(projectService.updateWarranty(id, warrantyId, dto));
    }
}
