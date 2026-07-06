package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.IssueCloseDTO;
import com.problem.dto.IssueCreateDTO;
import com.problem.dto.IssueRecordCreateDTO;
import com.problem.dto.IssueReopenDTO;
import com.problem.dto.IssueUpdateDTO;
import com.problem.service.IssueAttachmentService;
import com.problem.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final IssueAttachmentService issueAttachmentService;

    @GetMapping
    public Result<?> listIssues(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) String currentStatus,
        @RequestParam(required = false) String closureStatus,
        @RequestParam(required = false) String categoryKeyword,
        @RequestParam(required = false) String causeCategory,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String systemType,
        @RequestParam(required = false) String tagKeyword,
        @RequestParam(required = false) String ownerName,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long pageSize
    ) {
        return Result.success(issueService.listIssues(projectId, currentStatus, closureStatus, categoryKeyword, causeCategory, source, priority, severity, systemType, tagKeyword, ownerName, keyword, startDate, endDate, page, pageSize));
    }

    @GetMapping("/overview")
    public Result<?> getOverview(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) String currentStatus,
        @RequestParam(required = false) String closureStatus,
        @RequestParam(required = false) String categoryKeyword,
        @RequestParam(required = false) String causeCategory,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String systemType,
        @RequestParam(required = false) String tagKeyword,
        @RequestParam(required = false) String ownerName,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate
    ) {
        return Result.success(issueService.getOverview(projectId, currentStatus, closureStatus, categoryKeyword, causeCategory, source, priority, severity, systemType, tagKeyword, ownerName, keyword, startDate, endDate));
    }

    @GetMapping("/similar-search")
    public Result<?> similarSearch(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long projectId,
        @RequestParam(defaultValue = "10") Integer limit
    ) {
        return Result.success(issueService.similarSearch(keyword, projectId, limit));
    }

    @GetMapping("/{id}")
    public Result<?> getIssueDetail(@PathVariable Long id) {
        return Result.success(issueService.getIssueDetail(id));
    }

    @GetMapping("/{id}/attachments")
    public Result<?> listAttachments(@PathVariable Long id) {
        return Result.success(issueAttachmentService.listAttachments(id));
    }

    @PostMapping("/{id}/attachments")
    public Result<?> uploadAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.success(issueAttachmentService.uploadAttachment(id, file));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public Result<?> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        issueAttachmentService.deleteAttachment(id, attachmentId);
        return Result.success("附件已删除");
    }

    @PostMapping
    public Result<?> createIssue(@Valid @RequestBody IssueCreateDTO dto) {
        return Result.success(issueService.createIssue(dto));
    }

    @PutMapping("/{id}")
    public Result<?> updateIssue(@PathVariable Long id, @RequestBody IssueUpdateDTO dto) {
        return Result.success(issueService.updateIssue(id, dto));
    }

    @PostMapping("/{id}/records")
    public Result<?> addRecord(@PathVariable Long id, @Valid @RequestBody IssueRecordCreateDTO dto) {
        return Result.success(issueService.addRecord(id, dto));
    }

    @PostMapping("/{id}/close")
    public Result<?> closeIssue(@PathVariable Long id, @RequestBody IssueCloseDTO dto) {
        return Result.success(issueService.closeIssue(id, dto));
    }

    @PostMapping("/{id}/reopen")
    public Result<?> reopenIssue(@PathVariable Long id, @Valid @RequestBody IssueReopenDTO dto) {
        return Result.success(issueService.reopenIssue(id, dto));
    }
}
