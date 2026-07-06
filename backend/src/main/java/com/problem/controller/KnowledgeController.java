package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.KnowledgeUpdateDTO;
import com.problem.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public Result<?> listKnowledge(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) String sourceType,
        @RequestParam(required = false) String systemType,
        @RequestParam(required = false) String faultCode,
        @RequestParam(required = false) String tagKeyword,
        @RequestParam(required = false) String causeCategory,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long pageSize
    ) {
        return Result.success(knowledgeService.listKnowledge(projectId, sourceType, systemType, faultCode, tagKeyword, causeCategory, keyword, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<?> getKnowledge(@PathVariable Long id) {
        return Result.success(knowledgeService.getKnowledge(id));
    }

    @PostMapping("/from-issue/{issueId}")
    public Result<?> createFromIssue(@PathVariable Long issueId) {
        return Result.success(knowledgeService.syncFromIssueId(issueId));
    }

    @PostMapping("/sync-closed")
    public Result<?> syncClosedIssues() {
        return Result.success(knowledgeService.syncClosedIssues());
    }

    @PostMapping("/documents/upload")
    public Result<?> uploadKnowledgeDocument(@RequestPart("file") MultipartFile file) {
        return Result.success(knowledgeService.importCompanyExcel(file));
    }

    @PutMapping("/{id}")
    public Result<?> updateKnowledge(@PathVariable Long id, @RequestBody KnowledgeUpdateDTO dto) {
        return Result.success(knowledgeService.updateKnowledge(id, dto));
    }

    @PatchMapping("/{id}/publish")
    public Result<?> publishKnowledge(@PathVariable Long id) {
        return Result.success(knowledgeService.publishKnowledge(id));
    }

    @PatchMapping("/{id}/disable")
    public Result<?> disableKnowledge(@PathVariable Long id) {
        return Result.success(knowledgeService.disableKnowledge(id));
    }
}
