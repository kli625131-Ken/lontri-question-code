package com.problem.controller;

import com.problem.common.Result;
import com.problem.dto.MaintenanceAssignmentSaveDTO;
import com.problem.dto.MaintenanceFindingSaveDTO;
import com.problem.dto.MaintenancePersonnelSaveDTO;
import com.problem.dto.MaintenanceQuoteItemSaveDTO;
import com.problem.dto.MaintenanceVisitCloseDTO;
import com.problem.dto.MaintenanceVisitSaveDTO;
import com.problem.service.MaintenanceService;
import com.problem.service.YunweiMaintenanceImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final YunweiMaintenanceImportService yunweiMaintenanceImportService;

    @GetMapping("/visits")
    public Result<?> listVisits(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer quarter,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long pageSize
    ) {
        return Result.success(maintenanceService.listVisits(projectId, status, year, quarter, startDate, endDate, keyword, page, pageSize));
    }

    @GetMapping("/overview")
    public Result<?> getOverview(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer quarter,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate
    ) {
        return Result.success(maintenanceService.getOverview(projectId, year, quarter, startDate, endDate));
    }

    @PostMapping("/visits")
    public Result<?> createVisit(@Valid @RequestBody MaintenanceVisitSaveDTO dto) {
        return Result.success(maintenanceService.createVisit(dto));
    }

    @PostMapping("/import/yunwei")
    public Result<?> importYunwei() {
        return Result.success(yunweiMaintenanceImportService.importYunwei());
    }

    @GetMapping("/visits/{id}")
    public Result<?> getVisit(@PathVariable Long id) {
        return Result.success(maintenanceService.getVisit(id));
    }

    @GetMapping("/findings/{id}/visit")
    public Result<?> getVisitByFinding(@PathVariable Long id) {
        return Result.success(maintenanceService.getVisitByFindingId(id));
    }

    @PutMapping("/visits/{id}")
    public Result<?> updateVisit(@PathVariable Long id, @RequestBody MaintenanceVisitSaveDTO dto) {
        return Result.success(maintenanceService.updateVisit(id, dto));
    }

    @PostMapping("/visits/{id}/start")
    public Result<?> startVisit(@PathVariable Long id) {
        return Result.success(maintenanceService.startVisit(id));
    }

    @PostMapping("/visits/{id}/close")
    public Result<?> closeVisit(@PathVariable Long id, @RequestBody MaintenanceVisitCloseDTO dto) {
        return Result.success(maintenanceService.closeVisit(id, dto));
    }

    @PostMapping("/visits/{id}/assignments")
    public Result<?> createAssignment(@PathVariable Long id, @RequestBody MaintenanceAssignmentSaveDTO dto) {
        return Result.success(maintenanceService.createAssignment(id, dto));
    }

    @PutMapping("/visits/{id}/assignments/{assignmentId}")
    public Result<?> updateAssignment(@PathVariable Long id, @PathVariable Long assignmentId, @RequestBody MaintenanceAssignmentSaveDTO dto) {
        return Result.success(maintenanceService.updateAssignment(id, assignmentId, dto));
    }

    @DeleteMapping("/visits/{id}/assignments/{assignmentId}")
    public Result<?> deleteAssignment(@PathVariable Long id, @PathVariable Long assignmentId) {
        maintenanceService.deleteAssignment(id, assignmentId);
        return Result.success("运维前安排已删除");
    }

    @PostMapping("/visits/{id}/personnel")
    public Result<?> createPersonnel(@PathVariable Long id, @RequestBody MaintenancePersonnelSaveDTO dto) {
        return Result.success(maintenanceService.createPersonnel(id, dto));
    }

    @PutMapping("/visits/{id}/personnel/{personnelId}")
    public Result<?> updatePersonnel(@PathVariable Long id, @PathVariable Long personnelId, @RequestBody MaintenancePersonnelSaveDTO dto) {
        return Result.success(maintenanceService.updatePersonnel(id, personnelId, dto));
    }

    @DeleteMapping("/visits/{id}/personnel/{personnelId}")
    public Result<?> deletePersonnel(@PathVariable Long id, @PathVariable Long personnelId) {
        maintenanceService.deletePersonnel(id, personnelId);
        return Result.success("人员报备已删除");
    }

    @PostMapping("/visits/{id}/findings")
    public Result<?> createFinding(@PathVariable Long id, @RequestBody MaintenanceFindingSaveDTO dto) {
        return Result.success(maintenanceService.createFinding(id, dto));
    }

    @PutMapping("/visits/{id}/findings/{findingId}")
    public Result<?> updateFinding(@PathVariable Long id, @PathVariable Long findingId, @RequestBody MaintenanceFindingSaveDTO dto) {
        return Result.success(maintenanceService.updateFinding(id, findingId, dto));
    }

    @DeleteMapping("/visits/{id}/findings/{findingId}")
    public Result<?> deleteFinding(@PathVariable Long id, @PathVariable Long findingId) {
        maintenanceService.deleteFinding(id, findingId);
        return Result.success("现场记录已删除");
    }

    @PostMapping("/visits/{id}/quote-items")
    public Result<?> createQuoteItem(@PathVariable Long id, @RequestBody MaintenanceQuoteItemSaveDTO dto) {
        return Result.success(maintenanceService.createQuoteItem(id, dto));
    }

    @PutMapping("/visits/{id}/quote-items/{quoteItemId}")
    public Result<?> updateQuoteItem(@PathVariable Long id, @PathVariable Long quoteItemId, @RequestBody MaintenanceQuoteItemSaveDTO dto) {
        return Result.success(maintenanceService.updateQuoteItem(id, quoteItemId, dto));
    }

    @DeleteMapping("/visits/{id}/quote-items/{quoteItemId}")
    public Result<?> deleteQuoteItem(@PathVariable Long id, @PathVariable Long quoteItemId) {
        maintenanceService.deleteQuoteItem(id, quoteItemId);
        return Result.success("报价项已删除");
    }

    @PostMapping("/findings/{id}/attachments")
    public Result<?> uploadFindingAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.success(maintenanceService.uploadFindingAttachment(id, file));
    }

    @GetMapping("/visits/{id}/report")
    public Result<?> getReport(@PathVariable Long id) {
        return Result.success(maintenanceService.getReport(id));
    }

    @GetMapping("/visits/{id}/report/export-excel")
    public ResponseEntity<ByteArrayResource> exportReportExcel(@PathVariable Long id) {
        byte[] data = maintenanceService.exportReportExcel(id);
        String fileName = URLEncoder.encode("运维报告-" + id + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(data.length)
            .body(new ByteArrayResource(data));
    }
}
