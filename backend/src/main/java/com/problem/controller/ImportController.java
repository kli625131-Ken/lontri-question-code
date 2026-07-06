package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.ImportRowReviewUpdateDTO;
import com.problem.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @Value("${ops.import.default-remind-after-days:7}")
    private int defaultRemindAfterDays;

    @PostMapping("/excel")
    public Result<?> previewExcel(@RequestPart("file") MultipartFile file) {
        return Result.success(importService.previewExcel(file, defaultRemindAfterDays));
    }

    @GetMapping
    public Result<?> listBatches() {
        return Result.success(importService.listBatches());
    }

    @GetMapping("/{id}")
    public Result<?> getBatchDetail(@PathVariable Long id) {
        return Result.success(importService.getBatchDetail(id));
    }

    @PutMapping("/{batchId}/rows/{rowId}")
    public Result<?> updateRow(@PathVariable Long batchId, @PathVariable Long rowId, @RequestBody ImportRowReviewUpdateDTO dto) {
        return Result.success(importService.updateRow(batchId, rowId, dto));
    }

    @PostMapping("/{id}/commit")
    public Result<?> commitBatch(@PathVariable Long id) {
        return Result.success(importService.commitBatch(id));
    }
}
