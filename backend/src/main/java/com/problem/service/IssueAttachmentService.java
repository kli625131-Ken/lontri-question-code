package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.entity.OpsIssue;
import com.problem.entity.OpsIssueAttachment;
import com.problem.entity.User;
import com.problem.mapper.OpsIssueAttachmentMapper;
import com.problem.mapper.OpsIssueMapper;
import com.problem.mapper.UserMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.vo.IssueAttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final OpsIssueAttachmentMapper attachmentMapper;
    private final OpsIssueMapper issueMapper;
    private final UserMapper userMapper;
    private final CurrentUserAccessService currentUserAccessService;

    @Value("${ops.upload.root-path:./uploads}")
    private String uploadRootPath;

    @Value("${ops.upload.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes;

    public List<IssueAttachmentVO> listAttachments(Long issueId) {
        currentUserAccessService.assertIssueAccess(issueId);
        User currentUser = currentUserAccessService.getCurrentUser();
        List<OpsIssueAttachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<OpsIssueAttachment>()
            .eq(OpsIssueAttachment::getIssueId, issueId)
            .orderByDesc(OpsIssueAttachment::getCreatedAt));
        Map<Long, User> userMap = loadUserMap(attachments);
        return attachments.stream()
            .map(attachment -> toVO(attachment, userMap.get(attachment.getUploadedBy()), currentUser))
            .toList();
    }

    @Transactional
    public IssueAttachmentVO uploadAttachment(Long issueId, MultipartFile file) {
        currentUserAccessService.assertIssueAccess(issueId);
        OpsIssue issue = requireIssue(issueId);
        User currentUser = currentUserAccessService.getCurrentUser();
        validateFile(file);

        String originalFileName = StringUtils.cleanPath(defaultIfBlank(file.getOriginalFilename(), "attachment"));
        String extension = extensionOf(originalFileName);
        String storedFileName = UUID.randomUUID() + "." + extension;
        String issueNo = safePathSegment(defaultIfBlank(issue.getIssueNo(), "ISS-" + issue.getId()));
        String relativePath = "issues/" + issueNo + "/" + storedFileName;
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

        OpsIssueAttachment attachment = new OpsIssueAttachment();
        attachment.setIssueId(issueId);
        attachment.setFileName(originalFileName);
        attachment.setFileType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setFilePath(relativePath.replace("\\", "/"));
        attachment.setUploadedBy(currentUser.getId());
        attachment.setCreatedAt(LocalDateTime.now());
        attachment.setDeletedFlag(0);
        attachmentMapper.insert(attachment);
        return toVO(attachment, currentUser, currentUser);
    }

    @Transactional
    public void deleteAttachment(Long issueId, Long attachmentId) {
        currentUserAccessService.assertIssueAccess(issueId);
        User currentUser = currentUserAccessService.getCurrentUser();
        OpsIssueAttachment attachment = attachmentMapper.selectOne(new LambdaQueryWrapper<OpsIssueAttachment>()
            .eq(OpsIssueAttachment::getId, attachmentId)
            .eq(OpsIssueAttachment::getIssueId, issueId)
            .last("LIMIT 1"));
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }
        if (!canDelete(attachment, currentUser)) {
            throw new IllegalArgumentException("无权删除该附件");
        }
        attachmentMapper.deleteById(attachmentId);
        deletePhysicalFileQuietly(attachment.getFilePath());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String originalFileName = StringUtils.cleanPath(defaultIfBlank(file.getOriginalFilename(), ""));
        String extension = extensionOf(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、webp 图片");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、webp 图片");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("文件超过 10MB，请压缩后再上传");
        }
    }

    private OpsIssue requireIssue(Long issueId) {
        OpsIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("问题不存在");
        }
        return issue;
    }

    private IssueAttachmentVO toVO(OpsIssueAttachment attachment, User uploader, User currentUser) {
        String url = "/uploads/" + attachment.getFilePath();
        return IssueAttachmentVO.builder()
            .id(attachment.getId())
            .issueId(attachment.getIssueId())
            .fileName(attachment.getFileName())
            .fileType(attachment.getFileType())
            .fileSize(attachment.getFileSize())
            .filePath(attachment.getFilePath())
            .previewUrl(url)
            .downloadUrl(url)
            .uploadedBy(attachment.getUploadedBy())
            .uploadedByName(uploader == null ? null : defaultIfBlank(uploader.getRealName(), uploader.getUsername()))
            .createdAt(attachment.getCreatedAt())
            .canDelete(canDelete(attachment, currentUser))
            .build();
    }

    private boolean canDelete(OpsIssueAttachment attachment, User user) {
        return currentUserAccessService.isAdmin(user) || Objects.equals(attachment.getUploadedBy(), user.getId());
    }

    private Map<Long, User> loadUserMap(List<OpsIssueAttachment> attachments) {
        List<Long> userIds = attachments.stream()
            .map(OpsIssueAttachment::getUploadedBy)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
    }

    private void deletePhysicalFileQuietly(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Path root = Path.of(uploadRootPath).toAbsolutePath().normalize();
            Path target = root.resolve(filePath).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // 物理文件删除失败不影响附件逻辑删除。
        }
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

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
