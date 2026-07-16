package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.dto.MaintenanceAssignmentSaveDTO;
import com.problem.dto.MaintenanceFindingSaveDTO;
import com.problem.dto.MaintenancePersonnelSaveDTO;
import com.problem.dto.MaintenanceQuoteItemSaveDTO;
import com.problem.dto.MaintenanceVisitCloseDTO;
import com.problem.dto.MaintenanceVisitSaveDTO;
import com.problem.entity.OpsMaintenanceAssignment;
import com.problem.entity.OpsMaintenanceAttachment;
import com.problem.entity.OpsMaintenanceFinding;
import com.problem.entity.OpsMaintenancePersonnel;
import com.problem.entity.OpsMaintenanceQuoteItem;
import com.problem.entity.OpsMaintenanceSourceFile;
import com.problem.entity.OpsMaintenanceVisit;
import com.problem.entity.Project;
import com.problem.entity.User;
import com.problem.mapper.OpsMaintenanceAssignmentMapper;
import com.problem.mapper.OpsMaintenanceAttachmentMapper;
import com.problem.mapper.OpsMaintenanceFindingMapper;
import com.problem.mapper.OpsMaintenancePersonnelMapper;
import com.problem.mapper.OpsMaintenanceQuoteItemMapper;
import com.problem.mapper.OpsMaintenanceSourceFileMapper;
import com.problem.mapper.OpsMaintenanceVisitMapper;
import com.problem.mapper.ProjectMapper;
import com.problem.mapper.UserMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.vo.MaintenanceAssignmentVO;
import com.problem.vo.MaintenanceAttachmentVO;
import com.problem.vo.MaintenanceFindingVO;
import com.problem.vo.MaintenanceOverviewVO;
import com.problem.vo.MaintenancePersonnelVO;
import com.problem.vo.MaintenanceQuoteItemVO;
import com.problem.vo.MaintenanceSourceFileVO;
import com.problem.vo.MaintenanceVisitVO;
import com.problem.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_CLOSED = "CLOSED";

    private static final DateTimeFormatter VISIT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final OpsMaintenanceVisitMapper visitMapper;
    private final OpsMaintenanceAssignmentMapper assignmentMapper;
    private final OpsMaintenancePersonnelMapper personnelMapper;
    private final OpsMaintenanceFindingMapper findingMapper;
    private final OpsMaintenanceQuoteItemMapper quoteItemMapper;
    private final OpsMaintenanceAttachmentMapper attachmentMapper;
    private final OpsMaintenanceSourceFileMapper sourceFileMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final CurrentUserAccessService currentUserAccessService;
    private final KnowledgeService knowledgeService;

    @Value("${ops.upload.root-path:./uploads}")
    private String uploadRootPath;

    @Value("${ops.upload.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes;

    public PageResultVO<MaintenanceVisitVO> listVisits(
        Long projectId,
        String status,
        Integer year,
        Integer quarter,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String keyword,
        long page,
        long pageSize
    ) {
        User user = currentUserAccessService.getCurrentUser();
        LambdaQueryWrapper<OpsMaintenanceVisit> wrapper = buildVisitWrapper(user, projectId, status, year, quarter, startDate, endDate, keyword)
            .orderByDesc(OpsMaintenanceVisit::getPlannedStartAt, OpsMaintenanceVisit::getCreateTime);
        if (currentUserAccessService.hasNoVisibleProjects(user)) {
            return new PageResultVO<>(0, safePage(page), safePageSize(pageSize), List.of());
        }
        List<OpsMaintenanceVisit> visits = visitMapper.selectList(wrapper);
        Map<Long, Project> projectMap = loadProjectMap(visits.stream().map(OpsMaintenanceVisit::getProjectId).collect(Collectors.toSet()));
        Map<Long, VisitStats> statsMap = loadVisitStats(visits.stream().map(OpsMaintenanceVisit::getId).collect(Collectors.toSet()));
        List<MaintenanceVisitVO> items = visits.stream()
            .map(visit -> toVisitVO(visit, projectMap.get(visit.getProjectId()), statsMap.get(visit.getId()), false))
            .toList();
        return paginate(items, page, pageSize);
    }

    public MaintenanceOverviewVO getOverview(
        Long projectId,
        Integer year,
        Integer quarter,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        User user = currentUserAccessService.getCurrentUser();
        if (currentUserAccessService.hasNoVisibleProjects(user)) {
            return emptyOverview();
        }
        List<OpsMaintenanceVisit> visits = visitMapper.selectList(buildVisitWrapper(user, projectId, null, year, quarter, startDate, endDate, null));
        Set<Long> visitIds = visits.stream().map(OpsMaintenanceVisit::getId).collect(Collectors.toSet());
        List<OpsMaintenanceFinding> findings = visitIds.isEmpty() ? List.of() : findingMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceFinding>().in(OpsMaintenanceFinding::getVisitId, visitIds));
        List<OpsMaintenanceQuoteItem> quoteItems = visitIds.isEmpty() ? List.of() : quoteItemMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceQuoteItem>().in(OpsMaintenanceQuoteItem::getVisitId, visitIds));
        BigDecimal quoteTotal = quoteItems.stream()
            .map(item -> defaultAmount(item.getAmount(), item.getQuantity(), item.getUnitPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return MaintenanceOverviewVO.builder()
            .totalVisits(visits.size())
            .plannedVisits(visits.stream().filter(visit -> STATUS_PLANNED.equals(visit.getStatus())).count())
            .inProgressVisits(visits.stream().filter(visit -> STATUS_IN_PROGRESS.equals(visit.getStatus())).count())
            .closedVisits(visits.stream().filter(visit -> STATUS_CLOSED.equals(visit.getStatus())).count())
            .totalFindings(findings.size())
            .unresolvedFindings(findings.stream().filter(this::isUnresolved).count())
            .quoteTotalAmount(quoteTotal)
            .build();
    }

    public MaintenanceVisitVO getVisit(Long id) {
        OpsMaintenanceVisit visit = requireVisit(id);
        currentUserAccessService.assertProjectAccess(visit.getProjectId());
        return buildVisitDetail(visit);
    }

    public MaintenanceVisitVO getVisitByFindingId(Long findingId) {
        OpsMaintenanceFinding finding = requireFindingById(findingId);
        OpsMaintenanceVisit visit = requireVisit(finding.getVisitId());
        currentUserAccessService.assertProjectAccess(visit.getProjectId());
        return buildVisitDetail(visit);
    }

    @Transactional
    public MaintenanceVisitVO createVisit(MaintenanceVisitSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("新建运维批次");
        Project project = requireLeafProject(dto.getProjectId());
        User user = currentUserAccessService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        OpsMaintenanceVisit visit = new OpsMaintenanceVisit();
        applyVisitDto(visit, dto);
        visit.setStatus(normalizeVisitStatus(defaultIfBlank(dto.getStatus(), STATUS_PLANNED)));
        visit.setCreatedBy(user.getId());
        visit.setUpdatedBy(user.getId());
        visit.setCreateTime(now);
        visit.setUpdateTime(now);
        visit.setDeleted(0);
        visitMapper.insert(visit);
        visit.setVisitNo("MT-" + VISIT_NO_DATE_FORMATTER.format(now) + "-" + String.format("%05d", visit.getId()));
        if (!StringUtils.hasText(visit.getVisitTitle())) {
            visit.setVisitTitle(project.getProjectName() + " 运维巡检");
        }
        visitMapper.updateById(visit);
        return getVisit(visit.getId());
    }

    @Transactional
    public MaintenanceVisitVO updateVisit(Long id, MaintenanceVisitSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("编辑运维批次");
        OpsMaintenanceVisit visit = requireVisit(id);
        currentUserAccessService.assertProjectAccess(visit.getProjectId());
        if (dto.getProjectId() != null && !Objects.equals(dto.getProjectId(), visit.getProjectId())) {
            requireLeafProject(dto.getProjectId());
        }
        applyVisitDto(visit, dto);
        if (StringUtils.hasText(dto.getStatus())) {
            visit.setStatus(normalizeVisitStatus(dto.getStatus()));
        }
        visit.setUpdatedBy(currentUserAccessService.getCurrentUser().getId());
        visitMapper.updateById(visit);
        return getVisit(id);
    }

    @Transactional
    public MaintenanceVisitVO startVisit(Long id) {
        currentUserAccessService.assertNotTemporary("开始运维批次");
        OpsMaintenanceVisit visit = requireVisit(id);
        currentUserAccessService.assertProjectAccess(visit.getProjectId());
        visit.setStatus(STATUS_IN_PROGRESS);
        if (visit.getActualStartAt() == null) {
            visit.setActualStartAt(LocalDateTime.now());
        }
        visit.setUpdatedBy(currentUserAccessService.getCurrentUser().getId());
        visitMapper.updateById(visit);
        return getVisit(id);
    }

    @Transactional
    public MaintenanceVisitVO closeVisit(Long id, MaintenanceVisitCloseDTO dto) {
        currentUserAccessService.assertNotTemporary("关闭运维批次");
        OpsMaintenanceVisit visit = requireVisit(id);
        currentUserAccessService.assertProjectAccess(visit.getProjectId());
        visit.setStatus(STATUS_CLOSED);
        visit.setSummary(trimToNull(dto.getSummary()));
        visit.setConclusion(trimToNull(dto.getConclusion()));
        visit.setActualEndAt(dto.getActualEndAt() == null ? LocalDateTime.now() : dto.getActualEndAt());
        if (visit.getActualStartAt() == null) {
            visit.setActualStartAt(firstNonNull(visit.getPlannedStartAt(), visit.getCreateTime()));
        }
        visit.setUpdatedBy(currentUserAccessService.getCurrentUser().getId());
        visitMapper.updateById(visit);
        syncVisitFindingsToKnowledge(visit);
        return getVisit(id);
    }

    @Transactional
    public MaintenanceAssignmentVO createAssignment(Long visitId, MaintenanceAssignmentSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("维护运维前安排");
        requireVisitAccess(visitId);
        OpsMaintenanceAssignment assignment = new OpsMaintenanceAssignment();
        assignment.setVisitId(visitId);
        applyAssignmentDto(assignment, dto);
        assignment.setDeleted(0);
        assignmentMapper.insert(assignment);
        return toAssignmentVO(assignment);
    }

    @Transactional
    public MaintenanceAssignmentVO updateAssignment(Long visitId, Long id, MaintenanceAssignmentSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("维护运维前安排");
        requireVisitAccess(visitId);
        OpsMaintenanceAssignment assignment = requireAssignment(visitId, id);
        applyAssignmentDto(assignment, dto);
        assignmentMapper.updateById(assignment);
        return toAssignmentVO(assignment);
    }

    @Transactional
    public void deleteAssignment(Long visitId, Long id) {
        currentUserAccessService.assertNotTemporary("维护运维前安排");
        requireVisitAccess(visitId);
        assignmentMapper.deleteById(requireAssignment(visitId, id).getId());
    }

    @Transactional
    public MaintenancePersonnelVO createPersonnel(Long visitId, MaintenancePersonnelSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("维护运维人员");
        requireVisitAccess(visitId);
        OpsMaintenancePersonnel personnel = new OpsMaintenancePersonnel();
        personnel.setVisitId(visitId);
        applyPersonnelDto(personnel, dto);
        personnel.setDeleted(0);
        personnelMapper.insert(personnel);
        return toPersonnelVO(personnel);
    }

    @Transactional
    public MaintenancePersonnelVO updatePersonnel(Long visitId, Long id, MaintenancePersonnelSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("维护运维人员");
        requireVisitAccess(visitId);
        OpsMaintenancePersonnel personnel = requirePersonnel(visitId, id);
        applyPersonnelDto(personnel, dto);
        personnelMapper.updateById(personnel);
        return toPersonnelVO(personnel);
    }

    @Transactional
    public void deletePersonnel(Long visitId, Long id) {
        currentUserAccessService.assertNotTemporary("维护运维人员");
        requireVisitAccess(visitId);
        personnelMapper.deleteById(requirePersonnel(visitId, id).getId());
    }

    @Transactional
    public MaintenanceFindingVO createFinding(Long visitId, MaintenanceFindingSaveDTO dto) {
        OpsMaintenanceVisit visit = requireVisitAccess(visitId);
        User user = currentUserAccessService.getCurrentUser();
        OpsMaintenanceFinding finding = new OpsMaintenanceFinding();
        finding.setVisitId(visit.getId());
        applyFindingDto(finding, dto);
        finding.setCreatedBy(user.getId());
        finding.setUpdatedBy(user.getId());
        finding.setDeleted(0);
        findingMapper.insert(finding);
        return toFindingVO(finding, List.of(), user);
    }

    @Transactional
    public MaintenanceFindingVO updateFinding(Long visitId, Long id, MaintenanceFindingSaveDTO dto) {
        requireVisitAccess(visitId);
        OpsMaintenanceFinding finding = requireFinding(visitId, id);
        assertFindingWritable(finding);
        applyFindingDto(finding, dto);
        finding.setUpdatedBy(currentUserAccessService.getCurrentUser().getId());
        findingMapper.updateById(finding);
        syncFindingIfClosed(requireVisit(visitId), finding);
        return getVisit(visitId).getFindings().stream()
            .filter(item -> Objects.equals(item.getId(), id))
            .findFirst()
            .orElseGet(() -> toFindingVO(finding, List.of(), currentUserAccessService.getCurrentUser()));
    }

    @Transactional
    public void deleteFinding(Long visitId, Long id) {
        requireVisitAccess(visitId);
        OpsMaintenanceFinding finding = requireFinding(visitId, id);
        assertFindingWritable(finding);
        findingMapper.deleteById(finding.getId());
    }

    @Transactional
    public MaintenanceQuoteItemVO createQuoteItem(Long visitId, MaintenanceQuoteItemSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("维护报价");
        requireVisitAccess(visitId);
        OpsMaintenanceQuoteItem item = new OpsMaintenanceQuoteItem();
        item.setVisitId(visitId);
        applyQuoteDto(item, dto);
        item.setDeleted(0);
        quoteItemMapper.insert(item);
        return toQuoteItemVO(item);
    }

    @Transactional
    public MaintenanceQuoteItemVO updateQuoteItem(Long visitId, Long id, MaintenanceQuoteItemSaveDTO dto) {
        currentUserAccessService.assertNotTemporary("维护报价");
        requireVisitAccess(visitId);
        OpsMaintenanceQuoteItem item = requireQuoteItem(visitId, id);
        applyQuoteDto(item, dto);
        quoteItemMapper.updateById(item);
        return toQuoteItemVO(item);
    }

    @Transactional
    public void deleteQuoteItem(Long visitId, Long id) {
        currentUserAccessService.assertNotTemporary("维护报价");
        requireVisitAccess(visitId);
        quoteItemMapper.deleteById(requireQuoteItem(visitId, id).getId());
    }

    @Transactional
    public MaintenanceAttachmentVO uploadFindingAttachment(Long findingId, MultipartFile file) {
        OpsMaintenanceFinding finding = requireFindingById(findingId);
        OpsMaintenanceVisit visit = requireVisitAccess(finding.getVisitId());
        assertFindingWritable(finding);
        validateImage(file);
        User user = currentUserAccessService.getCurrentUser();
        String originalFileName = StringUtils.cleanPath(defaultIfBlank(file.getOriginalFilename(), "attachment"));
        String extension = extensionOf(originalFileName);
        String storedFileName = UUID.randomUUID() + "." + extension;
        String visitNo = safePathSegment(defaultIfBlank(visit.getVisitNo(), "MT-" + visit.getId()));
        String relativePath = "maintenance/" + visitNo + "/" + storedFileName;
        Path root = Path.of(uploadRootPath).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("文件保存路径不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("附件保存失败，请稍后重试");
        }
        OpsMaintenanceAttachment attachment = new OpsMaintenanceAttachment();
        attachment.setFindingId(finding.getId());
        attachment.setVisitId(visit.getId());
        attachment.setFileName(originalFileName);
        attachment.setFileType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setFilePath(relativePath.replace("\\", "/"));
        attachment.setUploadedBy(user.getId());
        attachment.setCreatedAt(LocalDateTime.now());
        attachment.setDeletedFlag(0);
        attachmentMapper.insert(attachment);
        return toAttachmentVO(attachment, user, user);
    }

    public MaintenanceVisitVO getReport(Long visitId) {
        return getVisit(visitId);
    }

    public byte[] exportReportExcel(Long visitId) {
        currentUserAccessService.assertNotTemporary("导出运维报告");
        MaintenanceVisitVO visit = getVisit(visitId);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            buildBasicSheet(workbook, visit, headerStyle);
            buildAssignmentSheet(workbook, visit.getAssignments(), headerStyle);
            buildFindingSheet(workbook, visit.getFindings(), headerStyle);
            buildQuoteSheet(workbook, visit.getQuoteItems(), headerStyle);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("运维报告导出失败");
        }
    }

    private LambdaQueryWrapper<OpsMaintenanceVisit> buildVisitWrapper(
        User user,
        Long projectId,
        String status,
        Integer year,
        Integer quarter,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String keyword
    ) {
        LambdaQueryWrapper<OpsMaintenanceVisit> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            currentUserAccessService.assertProjectAccess(projectId);
            wrapper.eq(OpsMaintenanceVisit::getProjectId, projectId);
        } else if (!currentUserAccessService.isAdmin(user)) {
            List<Long> scopedProjectIds = currentUserAccessService.scopeProjectIdsForList(user);
            if (!scopedProjectIds.isEmpty()) {
                wrapper.in(OpsMaintenanceVisit::getProjectId, scopedProjectIds);
            }
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OpsMaintenanceVisit::getStatus, normalizeVisitStatus(status));
        }
        if (year != null) {
            wrapper.eq(OpsMaintenanceVisit::getServiceYear, year);
        }
        if (quarter != null) {
            wrapper.eq(OpsMaintenanceVisit::getServiceQuarter, quarter);
        }
        if (startDate != null) {
            wrapper.ge(OpsMaintenanceVisit::getPlannedStartAt, startDate);
        }
        if (endDate != null) {
            wrapper.le(OpsMaintenanceVisit::getPlannedStartAt, endDate);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                .like(OpsMaintenanceVisit::getVisitNo, trimmed)
                .or()
                .like(OpsMaintenanceVisit::getVisitTitle, trimmed)
                .or()
                .like(OpsMaintenanceVisit::getServicePeriod, trimmed)
                .or()
                .like(OpsMaintenanceVisit::getSummary, trimmed)
                .or()
                .like(OpsMaintenanceVisit::getConclusion, trimmed));
        }
        return wrapper;
    }

    private MaintenanceVisitVO buildVisitDetail(OpsMaintenanceVisit visit) {
        Project project = projectMapper.selectById(visit.getProjectId());
        User user = currentUserAccessService.getCurrentUser();
        List<OpsMaintenanceAssignment> assignments = assignmentMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceAssignment>()
            .eq(OpsMaintenanceAssignment::getVisitId, visit.getId())
            .orderByAsc(OpsMaintenanceAssignment::getScheduledAt, OpsMaintenanceAssignment::getId));
        List<OpsMaintenancePersonnel> personnel = personnelMapper.selectList(new LambdaQueryWrapper<OpsMaintenancePersonnel>()
            .eq(OpsMaintenancePersonnel::getVisitId, visit.getId())
            .orderByAsc(OpsMaintenancePersonnel::getId));
        List<OpsMaintenanceFinding> findings = findingMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceFinding>()
            .eq(OpsMaintenanceFinding::getVisitId, visit.getId())
            .orderByAsc(OpsMaintenanceFinding::getFoundAt, OpsMaintenanceFinding::getId));
        List<OpsMaintenanceAttachment> attachments = findings.isEmpty() ? List.of() : attachmentMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceAttachment>()
            .in(OpsMaintenanceAttachment::getFindingId, findings.stream().map(OpsMaintenanceFinding::getId).toList())
            .orderByDesc(OpsMaintenanceAttachment::getCreatedAt));
        Map<Long, List<OpsMaintenanceAttachment>> attachmentMap = attachments.stream().collect(Collectors.groupingBy(OpsMaintenanceAttachment::getFindingId));
        Map<Long, User> userMap = loadUserMap(attachments);
        List<OpsMaintenanceQuoteItem> quoteItems = quoteItemMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceQuoteItem>()
            .eq(OpsMaintenanceQuoteItem::getVisitId, visit.getId())
            .orderByAsc(OpsMaintenanceQuoteItem::getId));
        List<OpsMaintenanceSourceFile> sourceFiles = sourceFileMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceSourceFile>()
            .eq(OpsMaintenanceSourceFile::getVisitId, visit.getId())
            .orderByAsc(OpsMaintenanceSourceFile::getFileType, OpsMaintenanceSourceFile::getFileName));
        VisitStats stats = calculateStats(findings, quoteItems);
        stats.assignmentCount = assignments.size();
        stats.personnelCount = personnel.size();
        MaintenanceVisitVO base = toVisitVO(visit, project, stats, false);
        base.setAssignments(assignments.stream().map(this::toAssignmentVO).toList());
        base.setPersonnel(personnel.stream().map(this::toPersonnelVO).toList());
        base.setFindings(findings.stream()
            .map(finding -> toFindingVO(finding, attachmentMap.getOrDefault(finding.getId(), List.of()), user, userMap))
            .toList());
        base.setQuoteItems(quoteItems.stream().map(this::toQuoteItemVO).toList());
        base.setSourceFiles(sourceFiles.stream().map(this::toSourceFileVO).toList());
        return base;
    }

    private void syncVisitFindingsToKnowledge(OpsMaintenanceVisit visit) {
        List<OpsMaintenanceFinding> findings = findingMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceFinding>()
            .eq(OpsMaintenanceFinding::getVisitId, visit.getId())
            .eq(OpsMaintenanceFinding::getKnowledgeIncluded, 1));
        findings.stream()
            .filter(finding -> StringUtils.hasText(finding.getIssueDescription()))
            .filter(finding -> !isUnresolved(finding))
            .forEach(finding -> knowledgeService.syncFromMaintenanceFinding(visit, finding));
    }

    private void syncFindingIfClosed(OpsMaintenanceVisit visit, OpsMaintenanceFinding finding) {
        if (STATUS_CLOSED.equals(visit.getStatus())
            && Objects.equals(defaultFlag(finding.getKnowledgeIncluded()), 1)
            && !isUnresolved(finding)) {
            knowledgeService.syncFromMaintenanceFinding(visit, finding);
        }
    }

    private void applyVisitDto(OpsMaintenanceVisit visit, MaintenanceVisitSaveDTO dto) {
        if (dto.getProjectId() != null) {
            visit.setProjectId(dto.getProjectId());
        }
        visit.setVisitTitle(trimToNull(dto.getVisitTitle()));
        visit.setServicePeriod(trimToNull(dto.getServicePeriod()));
        visit.setServiceYear(dto.getServiceYear());
        visit.setServiceQuarter(dto.getServiceQuarter());
        visit.setPlannedStartAt(dto.getPlannedStartAt());
        visit.setPlannedEndAt(dto.getPlannedEndAt());
        visit.setActualStartAt(dto.getActualStartAt());
        visit.setActualEndAt(dto.getActualEndAt());
        visit.setSummary(trimToNull(dto.getSummary()));
        visit.setConclusion(trimToNull(dto.getConclusion()));
        inferPeriod(visit);
    }

    private void inferPeriod(OpsMaintenanceVisit visit) {
        LocalDateTime basis = firstNonNull(visit.getPlannedStartAt(), firstNonNull(visit.getActualStartAt(), visit.getCreateTime()));
        if (basis == null) {
            basis = LocalDateTime.now();
        }
        if (visit.getServiceYear() == null) {
            visit.setServiceYear(basis.getYear());
        }
        if (visit.getServiceQuarter() == null) {
            visit.setServiceQuarter((basis.getMonthValue() - 1) / 3 + 1);
        }
        if (!StringUtils.hasText(visit.getServicePeriod())) {
            visit.setServicePeriod(visit.getServiceYear() + " Q" + visit.getServiceQuarter());
        }
    }

    private void applyAssignmentDto(OpsMaintenanceAssignment assignment, MaintenanceAssignmentSaveDTO dto) {
        assignment.setScheduledAt(dto.getScheduledAt());
        assignment.setFloorName(trimToNull(dto.getFloorName()));
        assignment.setTaskItem(trimToNull(dto.getTaskItem()));
        assignment.setOwnerName(trimToNull(dto.getOwnerName()));
        assignment.setStatus(defaultIfBlank(dto.getStatus(), "PENDING"));
        assignment.setNotes(trimToNull(dto.getNotes()));
    }

    private void applyPersonnelDto(OpsMaintenancePersonnel personnel, MaintenancePersonnelSaveDTO dto) {
        personnel.setPersonName(trimToNull(dto.getPersonName()));
        personnel.setPhone(trimToNull(dto.getPhone()));
        personnel.setRoleName(trimToNull(dto.getRoleName()));
        personnel.setNotes(trimToNull(dto.getNotes()));
    }

    private void applyFindingDto(OpsMaintenanceFinding finding, MaintenanceFindingSaveDTO dto) {
        finding.setFloorName(trimToNull(dto.getFloorName()));
        finding.setAreaName(trimToNull(dto.getAreaName()));
        finding.setIssueDescription(trimToNull(dto.getIssueDescription()));
        finding.setHandlingResult(trimToNull(dto.getHandlingResult()));
        finding.setCompletionStatus(defaultIfBlank(dto.getCompletionStatus(), "待处理"));
        finding.setCauseAnalysis(trimToNull(dto.getCauseAnalysis()));
        finding.setFollowUpAction(trimToNull(dto.getFollowUpAction()));
        finding.setQuoteRequired(defaultZero(dto.getQuoteRequired()));
        finding.setKnowledgeIncluded(defaultFlag(dto.getKnowledgeIncluded()));
        finding.setFoundAt(dto.getFoundAt() == null ? LocalDateTime.now() : dto.getFoundAt());
    }

    private void applyQuoteDto(OpsMaintenanceQuoteItem item, MaintenanceQuoteItemSaveDTO dto) {
        item.setAreaName(trimToNull(dto.getAreaName()));
        item.setItemName(trimToNull(dto.getItemName()));
        item.setQuantity(defaultDecimal(dto.getQuantity()));
        item.setUnitName(trimToNull(dto.getUnitName()));
        item.setUnitPrice(defaultDecimal(dto.getUnitPrice()));
        item.setAmount(defaultAmount(dto.getAmount(), dto.getQuantity(), dto.getUnitPrice()));
        item.setNotes(trimToNull(dto.getNotes()));
    }

    private OpsMaintenanceVisit requireVisitAccess(Long visitId) {
        OpsMaintenanceVisit visit = requireVisit(visitId);
        currentUserAccessService.assertProjectAccess(visit.getProjectId());
        return visit;
    }

    private OpsMaintenanceVisit requireVisit(Long id) {
        OpsMaintenanceVisit visit = visitMapper.selectById(id);
        if (visit == null) {
            throw new IllegalArgumentException("运维批次不存在");
        }
        return visit;
    }

    private OpsMaintenanceAssignment requireAssignment(Long visitId, Long id) {
        OpsMaintenanceAssignment assignment = assignmentMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceAssignment>()
            .eq(OpsMaintenanceAssignment::getVisitId, visitId)
            .eq(OpsMaintenanceAssignment::getId, id)
            .last("LIMIT 1"));
        if (assignment == null) {
            throw new IllegalArgumentException("运维前安排不存在");
        }
        return assignment;
    }

    private OpsMaintenancePersonnel requirePersonnel(Long visitId, Long id) {
        OpsMaintenancePersonnel personnel = personnelMapper.selectOne(new LambdaQueryWrapper<OpsMaintenancePersonnel>()
            .eq(OpsMaintenancePersonnel::getVisitId, visitId)
            .eq(OpsMaintenancePersonnel::getId, id)
            .last("LIMIT 1"));
        if (personnel == null) {
            throw new IllegalArgumentException("人员报备不存在");
        }
        return personnel;
    }

    private OpsMaintenanceFinding requireFinding(Long visitId, Long id) {
        OpsMaintenanceFinding finding = findingMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceFinding>()
            .eq(OpsMaintenanceFinding::getVisitId, visitId)
            .eq(OpsMaintenanceFinding::getId, id)
            .last("LIMIT 1"));
        if (finding == null) {
            throw new IllegalArgumentException("现场记录不存在");
        }
        return finding;
    }

    private OpsMaintenanceFinding requireFindingById(Long id) {
        OpsMaintenanceFinding finding = findingMapper.selectById(id);
        if (finding == null) {
            throw new IllegalArgumentException("现场记录不存在");
        }
        return finding;
    }

    private OpsMaintenanceQuoteItem requireQuoteItem(Long visitId, Long id) {
        OpsMaintenanceQuoteItem item = quoteItemMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceQuoteItem>()
            .eq(OpsMaintenanceQuoteItem::getVisitId, visitId)
            .eq(OpsMaintenanceQuoteItem::getId, id)
            .last("LIMIT 1"));
        if (item == null) {
            throw new IllegalArgumentException("报价项不存在");
        }
        return item;
    }

    private Project requireLeafProject(Long projectId) {
        currentUserAccessService.assertProjectAccess(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        if (StringUtils.hasText(project.getProjectLevel()) && !"PROJECT".equalsIgnoreCase(project.getProjectLevel())) {
            throw new IllegalArgumentException("运维批次必须绑定到子项目");
        }
        if (Objects.equals(project.getIsActive(), 0)) {
            throw new IllegalArgumentException("已禁用项目不能新建运维批次");
        }
        return project;
    }

    private void assertFindingWritable(OpsMaintenanceFinding finding) {
        User user = currentUserAccessService.getCurrentUser();
        if (!currentUserAccessService.isTemporary(user)) {
            return;
        }
        if (!Objects.equals(finding.getCreatedBy(), user.getId())) {
            throw new IllegalArgumentException("临时账号只能维护自己创建的现场记录");
        }
    }

    private Map<Long, Project> loadProjectMap(Set<Long> projectIds) {
        Set<Long> safeIds = projectIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (safeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectMapper.selectBatchIds(safeIds).stream()
            .collect(Collectors.toMap(Project::getId, project -> project, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, User> loadUserMap(List<OpsMaintenanceAttachment> attachments) {
        List<Long> userIds = attachments.stream().map(OpsMaintenanceAttachment::getUploadedBy).filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
    }

    private Map<Long, VisitStats> loadVisitStats(Set<Long> visitIds) {
        if (visitIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, VisitStats> statsMap = new LinkedHashMap<>();
        visitIds.forEach(id -> statsMap.put(id, new VisitStats()));
        assignmentMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceAssignment>().in(OpsMaintenanceAssignment::getVisitId, visitIds))
            .forEach(item -> statsMap.get(item.getVisitId()).assignmentCount++);
        personnelMapper.selectList(new LambdaQueryWrapper<OpsMaintenancePersonnel>().in(OpsMaintenancePersonnel::getVisitId, visitIds))
            .forEach(item -> statsMap.get(item.getVisitId()).personnelCount++);
        findingMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceFinding>().in(OpsMaintenanceFinding::getVisitId, visitIds))
            .forEach(item -> {
                VisitStats stats = statsMap.get(item.getVisitId());
                stats.findingCount++;
                if (isUnresolved(item)) {
                    stats.unresolvedFindingCount++;
                }
            });
        quoteItemMapper.selectList(new LambdaQueryWrapper<OpsMaintenanceQuoteItem>().in(OpsMaintenanceQuoteItem::getVisitId, visitIds))
            .forEach(item -> {
                VisitStats stats = statsMap.get(item.getVisitId());
                stats.quoteItemCount++;
                stats.quoteTotalAmount = stats.quoteTotalAmount.add(defaultAmount(item.getAmount(), item.getQuantity(), item.getUnitPrice()));
            });
        return statsMap;
    }

    private VisitStats calculateStats(List<OpsMaintenanceFinding> findings, List<OpsMaintenanceQuoteItem> quoteItems) {
        VisitStats stats = new VisitStats();
        stats.findingCount = findings.size();
        stats.unresolvedFindingCount = (int) findings.stream().filter(this::isUnresolved).count();
        stats.quoteItemCount = quoteItems.size();
        stats.quoteTotalAmount = quoteItems.stream()
            .map(item -> defaultAmount(item.getAmount(), item.getQuantity(), item.getUnitPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return stats;
    }

    private MaintenanceVisitVO toVisitVO(OpsMaintenanceVisit visit, Project project, VisitStats stats, boolean includeEmptyLists) {
        Project safeProject = project == null ? new Project() : project;
        VisitStats safeStats = stats == null ? new VisitStats() : stats;
        return MaintenanceVisitVO.builder()
            .id(visit.getId())
            .projectId(visit.getProjectId())
            .customerName(safeProject.getCustomerName())
            .projectGroup(safeProject.getProjectGroup())
            .projectName(safeProject.getProjectName())
            .projectCode(safeProject.getProjectCode())
            .visitNo(visit.getVisitNo())
            .visitTitle(visit.getVisitTitle())
            .servicePeriod(visit.getServicePeriod())
            .serviceYear(visit.getServiceYear())
            .serviceQuarter(visit.getServiceQuarter())
            .plannedStartAt(visit.getPlannedStartAt())
            .plannedEndAt(visit.getPlannedEndAt())
            .actualStartAt(visit.getActualStartAt())
            .actualEndAt(visit.getActualEndAt())
            .status(visit.getStatus())
            .summary(visit.getSummary())
            .conclusion(visit.getConclusion())
            .sourceFilePath(visit.getSourceFilePath())
            .sourceSheet(visit.getSourceSheet())
            .sourceRowNumber(visit.getSourceRowNumber())
            .sourceHash(visit.getSourceHash())
            .assignmentCount(safeStats.assignmentCount)
            .personnelCount(safeStats.personnelCount)
            .findingCount(safeStats.findingCount)
            .unresolvedFindingCount(safeStats.unresolvedFindingCount)
            .quoteItemCount(safeStats.quoteItemCount)
            .quoteTotalAmount(safeStats.quoteTotalAmount)
            .createTime(visit.getCreateTime())
            .updateTime(visit.getUpdateTime())
            .assignments(includeEmptyLists ? List.of() : null)
            .personnel(includeEmptyLists ? List.of() : null)
            .findings(includeEmptyLists ? List.of() : null)
            .quoteItems(includeEmptyLists ? List.of() : null)
            .sourceFiles(includeEmptyLists ? List.of() : null)
            .build();
    }

    private MaintenanceAssignmentVO toAssignmentVO(OpsMaintenanceAssignment assignment) {
        return MaintenanceAssignmentVO.builder()
            .id(assignment.getId())
            .visitId(assignment.getVisitId())
            .scheduledAt(assignment.getScheduledAt())
            .floorName(assignment.getFloorName())
            .taskItem(assignment.getTaskItem())
            .ownerName(assignment.getOwnerName())
            .status(assignment.getStatus())
            .notes(assignment.getNotes())
            .sourceFilePath(assignment.getSourceFilePath())
            .sourceSheet(assignment.getSourceSheet())
            .sourceRowNumber(assignment.getSourceRowNumber())
            .sourceHash(assignment.getSourceHash())
            .createTime(assignment.getCreateTime())
            .updateTime(assignment.getUpdateTime())
            .build();
    }

    private MaintenancePersonnelVO toPersonnelVO(OpsMaintenancePersonnel personnel) {
        return MaintenancePersonnelVO.builder()
            .id(personnel.getId())
            .visitId(personnel.getVisitId())
            .personName(personnel.getPersonName())
            .phone(personnel.getPhone())
            .roleName(personnel.getRoleName())
            .notes(personnel.getNotes())
            .sourceFilePath(personnel.getSourceFilePath())
            .sourceSheet(personnel.getSourceSheet())
            .sourceRowNumber(personnel.getSourceRowNumber())
            .sourceHash(personnel.getSourceHash())
            .createTime(personnel.getCreateTime())
            .updateTime(personnel.getUpdateTime())
            .build();
    }

    private MaintenanceFindingVO toFindingVO(OpsMaintenanceFinding finding, List<OpsMaintenanceAttachment> attachments, User currentUser) {
        return toFindingVO(finding, attachments, currentUser, Map.of());
    }

    private MaintenanceFindingVO toFindingVO(OpsMaintenanceFinding finding, List<OpsMaintenanceAttachment> attachments, User currentUser, Map<Long, User> userMap) {
        return MaintenanceFindingVO.builder()
            .id(finding.getId())
            .visitId(finding.getVisitId())
            .floorName(finding.getFloorName())
            .areaName(finding.getAreaName())
            .issueDescription(finding.getIssueDescription())
            .handlingResult(finding.getHandlingResult())
            .completionStatus(finding.getCompletionStatus())
            .causeAnalysis(finding.getCauseAnalysis())
            .followUpAction(finding.getFollowUpAction())
            .quoteRequired(finding.getQuoteRequired())
            .knowledgeIncluded(finding.getKnowledgeIncluded())
            .foundAt(finding.getFoundAt())
            .sourceFilePath(finding.getSourceFilePath())
            .sourceSheet(finding.getSourceSheet())
            .sourceRowNumber(finding.getSourceRowNumber())
            .sourceHash(finding.getSourceHash())
            .createdBy(finding.getCreatedBy())
            .updatedBy(finding.getUpdatedBy())
            .createTime(finding.getCreateTime())
            .updateTime(finding.getUpdateTime())
            .attachments(attachments.stream().map(item -> toAttachmentVO(item, userMap.get(item.getUploadedBy()), currentUser)).toList())
            .build();
    }

    private MaintenanceQuoteItemVO toQuoteItemVO(OpsMaintenanceQuoteItem item) {
        return MaintenanceQuoteItemVO.builder()
            .id(item.getId())
            .visitId(item.getVisitId())
            .areaName(item.getAreaName())
            .itemName(item.getItemName())
            .quantity(item.getQuantity())
            .unitName(item.getUnitName())
            .unitPrice(item.getUnitPrice())
            .amount(defaultAmount(item.getAmount(), item.getQuantity(), item.getUnitPrice()))
            .notes(item.getNotes())
            .sourceFilePath(item.getSourceFilePath())
            .sourceSheet(item.getSourceSheet())
            .sourceRowNumber(item.getSourceRowNumber())
            .sourceHash(item.getSourceHash())
            .createTime(item.getCreateTime())
            .updateTime(item.getUpdateTime())
            .build();
    }

    private MaintenanceSourceFileVO toSourceFileVO(OpsMaintenanceSourceFile file) {
        return MaintenanceSourceFileVO.builder()
            .id(file.getId())
            .visitId(file.getVisitId())
            .projectName(file.getProjectName())
            .fileType(file.getFileType())
            .fileName(file.getFileName())
            .filePath(file.getFilePath())
            .zipEntryPath(file.getZipEntryPath())
            .importStatus(file.getImportStatus())
            .message(file.getMessage())
            .sourceHash(file.getSourceHash())
            .createTime(file.getCreateTime())
            .updateTime(file.getUpdateTime())
            .build();
    }

    private MaintenanceAttachmentVO toAttachmentVO(OpsMaintenanceAttachment attachment, User uploader, User currentUser) {
        String url = "/uploads/" + attachment.getFilePath();
        return MaintenanceAttachmentVO.builder()
            .id(attachment.getId())
            .findingId(attachment.getFindingId())
            .visitId(attachment.getVisitId())
            .fileName(attachment.getFileName())
            .fileType(attachment.getFileType())
            .fileSize(attachment.getFileSize())
            .filePath(attachment.getFilePath())
            .previewUrl(url)
            .downloadUrl(url)
            .uploadedBy(attachment.getUploadedBy())
            .uploadedByName(uploader == null ? null : defaultIfBlank(uploader.getRealName(), uploader.getUsername()))
            .createdAt(attachment.getCreatedAt())
            .canDelete(currentUserAccessService.isAdmin(currentUser) || Objects.equals(attachment.getUploadedBy(), currentUser.getId()))
            .build();
    }

    private void buildBasicSheet(Workbook workbook, MaintenanceVisitVO visit, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("基本信息");
        String[][] rows = {
            {"运维编号", valueText(visit.getVisitNo())},
            {"项目", valueText(visit.getProjectName())},
            {"客户", valueText(visit.getCustomerName())},
            {"运维标题", valueText(visit.getVisitTitle())},
            {"周期", valueText(visit.getServicePeriod())},
            {"状态", valueText(visit.getStatus())},
            {"计划开始", valueText(visit.getPlannedStartAt())},
            {"计划结束", valueText(visit.getPlannedEndAt())},
            {"实际开始", valueText(visit.getActualStartAt())},
            {"实际结束", valueText(visit.getActualEndAt())},
            {"摘要", valueText(visit.getSummary())},
            {"结论", valueText(visit.getConclusion())}
        };
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(rows[i][0]);
            row.getCell(0).setCellStyle(headerStyle);
            row.createCell(1).setCellValue(rows[i][1]);
        }
        autosize(sheet, 2);
    }

    private void buildAssignmentSheet(Workbook workbook, List<MaintenanceAssignmentVO> assignments, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("运维前安排");
        writeHeader(sheet, headerStyle, "日期时间", "楼层", "事项", "负责人", "状态", "备注");
        int rowIndex = 1;
        for (MaintenanceAssignmentVO item : safeList(assignments)) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(valueText(item.getScheduledAt()));
            row.createCell(1).setCellValue(valueText(item.getFloorName()));
            row.createCell(2).setCellValue(valueText(item.getTaskItem()));
            row.createCell(3).setCellValue(valueText(item.getOwnerName()));
            row.createCell(4).setCellValue(valueText(item.getStatus()));
            row.createCell(5).setCellValue(valueText(item.getNotes()));
        }
        autosize(sheet, 6);
    }

    private void buildFindingSheet(Workbook workbook, List<MaintenanceFindingVO> findings, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("现场记录");
        writeHeader(sheet, headerStyle, "楼层", "位置/区域", "问题描述", "处理情况", "完成情况", "原因分析", "后续动作", "需要报价", "入知识库");
        int rowIndex = 1;
        for (MaintenanceFindingVO item : safeList(findings)) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(valueText(item.getFloorName()));
            row.createCell(1).setCellValue(valueText(item.getAreaName()));
            row.createCell(2).setCellValue(valueText(item.getIssueDescription()));
            row.createCell(3).setCellValue(valueText(item.getHandlingResult()));
            row.createCell(4).setCellValue(valueText(item.getCompletionStatus()));
            row.createCell(5).setCellValue(valueText(item.getCauseAnalysis()));
            row.createCell(6).setCellValue(valueText(item.getFollowUpAction()));
            row.createCell(7).setCellValue(Objects.equals(item.getQuoteRequired(), 1) ? "是" : "否");
            row.createCell(8).setCellValue(Objects.equals(item.getKnowledgeIncluded(), 1) ? "是" : "否");
        }
        autosize(sheet, 9);
    }

    private void buildQuoteSheet(Workbook workbook, List<MaintenanceQuoteItemVO> quoteItems, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("报价清单");
        writeHeader(sheet, headerStyle, "区域", "事项", "数量", "单位", "单价", "金额", "备注");
        int rowIndex = 1;
        for (MaintenanceQuoteItemVO item : safeList(quoteItems)) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(valueText(item.getAreaName()));
            row.createCell(1).setCellValue(valueText(item.getItemName()));
            row.createCell(2).setCellValue(item.getQuantity() == null ? 0 : item.getQuantity().doubleValue());
            row.createCell(3).setCellValue(valueText(item.getUnitName()));
            row.createCell(4).setCellValue(item.getUnitPrice() == null ? 0 : item.getUnitPrice().doubleValue());
            row.createCell(5).setCellValue(item.getAmount() == null ? 0 : item.getAmount().doubleValue());
            row.createCell(6).setCellValue(valueText(item.getNotes()));
        }
        autosize(sheet, 7);
    }

    private void writeHeader(Sheet sheet, CellStyle style, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
            row.getCell(i).setCellStyle(style);
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > 16000) {
                sheet.setColumnWidth(i, 16000);
            }
        }
    }

    private boolean isUnresolved(OpsMaintenanceFinding finding) {
        String status = defaultIfBlank(finding.getCompletionStatus(), "");
        return !containsAny(status, "已完成", "已解决", "完成", "解决", "closed", "done");
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String originalFileName = StringUtils.cleanPath(defaultIfBlank(file.getOriginalFilename(), ""));
        String extension = extensionOf(originalFileName);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、webp 图片");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、webp 图片");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("文件超过 10MB，请压缩后再上传");
        }
    }

    private PageResultVO<MaintenanceVisitVO> paginate(List<MaintenanceVisitVO> items, long page, long pageSize) {
        long safePage = safePage(page);
        long safePageSize = safePageSize(pageSize);
        int fromIndex = (int) Math.min(items.size(), (safePage - 1) * safePageSize);
        int toIndex = (int) Math.min(items.size(), fromIndex + safePageSize);
        return new PageResultVO<>(items.size(), safePage, safePageSize, items.subList(fromIndex, toIndex));
    }

    private MaintenanceOverviewVO emptyOverview() {
        return MaintenanceOverviewVO.builder()
            .totalVisits(0)
            .plannedVisits(0)
            .inProgressVisits(0)
            .closedVisits(0)
            .totalFindings(0)
            .unresolvedFindings(0)
            .quoteTotalAmount(BigDecimal.ZERO)
            .build();
    }

    private String normalizeVisitStatus(String status) {
        String value = defaultIfBlank(status, STATUS_PLANNED).toUpperCase(Locale.ROOT);
        return switch (value) {
            case STATUS_IN_PROGRESS, STATUS_CLOSED -> value;
            default -> STATUS_PLANNED;
        };
    }

    private Integer defaultFlag(Integer value) {
        return Objects.equals(value, 0) ? 0 : 1;
    }

    private Integer defaultZero(Integer value) {
        return Objects.equals(value, 1) ? 1 : 0;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal defaultAmount(BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice) {
        if (amount != null) {
            return amount;
        }
        return defaultDecimal(quantity).multiply(defaultDecimal(unitPrice));
    }

    private long safePage(long page) {
        return page < 1 ? 1 : page;
    }

    private long safePageSize(long pageSize) {
        return pageSize < 1 ? 20 : pageSize;
    }

    private boolean containsAny(String value, String... tokens) {
        String normalized = defaultIfBlank(value, "").toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extensionOf(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String safePathSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    private String valueText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static class VisitStats {
        private int assignmentCount;
        private int personnelCount;
        private int findingCount;
        private int unresolvedFindingCount;
        private int quoteItemCount;
        private BigDecimal quoteTotalAmount = BigDecimal.ZERO;
    }
}
