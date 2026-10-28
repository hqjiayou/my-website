package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminMediaConstants;
import com.mywebsite.admin.constant.AdminMediaMessageKeys;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaDetailDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListItemDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListQuery;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaPreviewDto;
import com.mywebsite.admin.entity.AdminContentEntity;
import com.mywebsite.admin.entity.AdminMediaEntity;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.mapper.AdminContentMapper;
import com.mywebsite.admin.mapper.AdminMediaMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.properties.AdminMediaProperties;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import com.mywebsite.infra.properties.StorageProperties;
import com.mywebsite.infra.service.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminMediaServiceImpl implements AdminMediaService {

    private static final Logger log = LoggerFactory.getLogger(AdminMediaServiceImpl.class);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final AdminMediaMapper adminMediaMapper;
    private final AdminAgentMapper adminAgentMapper;
    private final AdminContentMapper adminContentMapper;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;
    private final AdminMediaProperties mediaProperties;

    public AdminMediaServiceImpl(
            AdminMediaMapper adminMediaMapper,
            AdminAgentMapper adminAgentMapper,
            AdminContentMapper adminContentMapper,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties,
            AdminMediaProperties mediaProperties) {
        this.adminMediaMapper = adminMediaMapper;
        this.adminAgentMapper = adminAgentMapper;
        this.adminContentMapper = adminContentMapper;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.mediaProperties = mediaProperties;
    }

    @Override
    public PageResult<AdminMediaListItemDto> listMedia(long agentId, AdminMediaListQuery query) {
        requireSite(agentId);
        int pageNum = query != null && query.pageNum() != null
                ? query.pageNum()
                : AdminMediaConstants.DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), AdminMediaConstants.MAX_PAGE_SIZE)
                : AdminMediaConstants.DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminMediaEntity> wrapper = new LambdaQueryWrapper<AdminMediaEntity>()
                .eq(AdminMediaEntity::getAgentId, agentId)
                .orderByDesc(AdminMediaEntity::getId);
        if (query != null && StringUtils.hasText(query.contentTypePrefix())) {
            wrapper.likeRight(AdminMediaEntity::getContentType, query.contentTypePrefix().trim());
        }

        Page<AdminMediaEntity> page =
                adminMediaMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminMediaDetailDto getMedia(long agentId, long mediaId) {
        requireSite(agentId);
        return toDetail(requireMediaInSite(agentId, mediaId));
    }

    /**
     * 故意不加类级事务：UPLOADING 须先落库；put 失败时 FAILED 也需提交，供 orphan 扫尾。
     */
    @Override
    public AdminMediaDetailDto upload(long agentId, MultipartFile file) {
        requireSite(agentId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminMediaMessageKeys.MEDIA_FILE_REQUIRED);
        }

        String bucket = requireBucket();
        byte[] bytes = readBytes(file);
        String detectedContentType = detectContentType(bytes);
        if (detectedContentType == null
                || !AdminMediaConstants.ALLOWED_CONTENT_TYPES.contains(detectedContentType)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminMediaMessageKeys.MEDIA_TYPE_INVALID);
        }
        long maxBytes = maxBytesFor(detectedContentType);
        if (bytes.length > maxBytes) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminMediaMessageKeys.MEDIA_SIZE_EXCEEDED);
        }

        String extension = AdminMediaConstants.EXTENSION_BY_CONTENT_TYPE.get(detectedContentType);
        String objectKey = buildObjectKey(agentId, extension);
        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename(), extension);
        String sha256 = sha256Hex(bytes);

        AdminMediaEntity entity = new AdminMediaEntity();
        entity.setAgentId(agentId);
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setOriginalFilename(originalFilename);
        entity.setContentType(detectedContentType);
        entity.setExtension(extension);
        entity.setFileSize((long) bytes.length);
        entity.setSha256(sha256);
        entity.setVisibility(AdminMediaConstants.VISIBILITY_PRIVATE);
        entity.setStatus(AdminMediaConstants.STATUS_UPLOADING);
        adminMediaMapper.insert(entity);

        try {
            objectStorageService.put(
                    bucket, objectKey, new ByteArrayInputStream(bytes), bytes.length, detectedContentType);
            entity.setStatus(AdminMediaConstants.STATUS_READY);
            adminMediaMapper.updateById(entity);
        } catch (RuntimeException ex) {
            log.warn("Media upload put failed mediaId={} key={}", entity.getId(), objectKey, ex);
            entity.setStatus(AdminMediaConstants.STATUS_FAILED);
            adminMediaMapper.updateById(entity);
            tryRemoveObject(bucket, objectKey);
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR.getCode(), AdminMediaMessageKeys.MEDIA_UPLOAD_FAILED);
        }
        return toDetail(requireMediaInSite(agentId, entity.getId()));
    }

    @Override
    public AdminMediaPreviewDto preview(long agentId, long mediaId) {
        requireSite(agentId);
        AdminMediaEntity media = requireMediaInSite(agentId, mediaId);
        if (!AdminMediaConstants.STATUS_READY.equals(media.getStatus())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminMediaMessageKeys.MEDIA_NOT_READY);
        }
        var ttl = mediaProperties.getPresignTtl();
        String url = objectStorageService.presignedGet(media.getBucket(), media.getObjectKey(), ttl);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(ttl);
        return new AdminMediaPreviewDto(media.getId(), url, expiresAt);
    }

    @Override
    @Transactional
    public void deleteMedia(long agentId, long mediaId) {
        requireSite(agentId);
        AdminMediaEntity media = requireMediaInSite(agentId, mediaId);
        long contentRefs = adminContentMapper.selectCount(
                new LambdaQueryWrapper<AdminContentEntity>()
                        .eq(AdminContentEntity::getCoverMediaId, mediaId));
        if (contentRefs > 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminMediaMessageKeys.MEDIA_REFERENCED_BY_CONTENT);
        }
        media.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        adminMediaMapper.updateById(media);
        int deleted = adminMediaMapper.deleteById(media.getId());
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminMediaMessageKeys.MEDIA_NOT_FOUND);
        }
        // 尽力立即清对象；失败留给 orphan 任务
        tryRemoveObject(media.getBucket(), media.getObjectKey());
    }

    @Override
    @Transactional
    public void softDeleteByAgentId(long agentId) {
        List<AdminMediaEntity> mediaList = adminMediaMapper.selectList(
                new LambdaQueryWrapper<AdminMediaEntity>().eq(AdminMediaEntity::getAgentId, agentId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AdminMediaEntity media : mediaList) {
            media.setDeletedAt(now);
            adminMediaMapper.updateById(media);
            adminMediaMapper.deleteById(media.getId());
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${admin.media.orphan-fixed-delay-ms:900000}")
    public void purgeOrphans() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime deletedBefore = now.minus(mediaProperties.getOrphanRetention());
        OffsetDateTime staleBefore = now.minus(mediaProperties.getOrphanStaleUpload());
        List<AdminMediaEntity> candidates = adminMediaMapper.selectOrphanCandidates(
                deletedBefore, staleBefore, mediaProperties.getOrphanBatchSize());
        for (AdminMediaEntity media : candidates) {
            // 仅 remove 成功才打标，失败留给下轮；打标后不再入选，batch 才能推进
            if (!tryRemoveObject(media.getBucket(), media.getObjectKey())) {
                continue;
            }
            adminMediaMapper.markObjectPurged(media.getId(), now);
            if (Boolean.FALSE.equals(media.getDeleted())
                    && (AdminMediaConstants.STATUS_FAILED.equals(media.getStatus())
                            || AdminMediaConstants.STATUS_UPLOADING.equals(media.getStatus()))) {
                adminMediaMapper.deleteById(media.getId());
            }
        }
    }

    private AdminAgentEntity requireSite(long agentId) {
        AdminAgentEntity agent = adminAgentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.SITE_NOT_FOUND);
        }
        return agent;
    }

    private AdminMediaEntity requireMediaInSite(long agentId, long mediaId) {
        AdminMediaEntity media = adminMediaMapper.selectById(mediaId);
        // 跨站或不存在统一 404，避免泄露归属
        if (media == null || !Long.valueOf(agentId).equals(media.getAgentId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminMediaMessageKeys.MEDIA_NOT_FOUND);
        }
        return media;
    }

    private String requireBucket() {
        String bucket = storageProperties.getBucket();
        if (!StringUtils.hasText(bucket)) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR.getCode(), AdminMediaMessageKeys.MEDIA_STORAGE_MISCONFIGURED);
        }
        return bucket;
    }

    private long maxBytesFor(String contentType) {
        if (AdminMediaConstants.MIME_PDF.equals(contentType)) {
            return mediaProperties.getMaxPdfBytes();
        }
        return mediaProperties.getMaxImageBytes();
    }

    private static String buildObjectKey(long agentId, String extension) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return "sites/"
                + agentId
                + "/"
                + YEAR.format(now)
                + "/"
                + MONTH.format(now)
                + "/"
                + UUID.randomUUID()
                + "."
                + extension;
    }

    private static String sanitizeOriginalFilename(String original, String extension) {
        String name = StringUtils.hasText(original) ? original.trim() : ("file." + extension);
        // 仅保留展示用短名；绝不拼进 object key
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (!StringUtils.hasText(name) || name.equals(".") || name.equals("..")) {
            name = "file." + extension;
        }
        if (name.length() > AdminMediaConstants.MAX_ORIGINAL_FILENAME_LENGTH) {
            name = name.substring(0, AdminMediaConstants.MAX_ORIGINAL_FILENAME_LENGTH);
        }
        return name;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminMediaMessageKeys.MEDIA_FILE_REQUIRED);
        }
    }

    /**
     * 魔数优先的内容 MIME 检测；不信任客户端 Content-Type / 扩展名。拒 SVG/HTML。
     */
    static String detectContentType(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        if (isJpeg(data)) {
            return AdminMediaConstants.MIME_JPEG;
        }
        if (isPng(data)) {
            return AdminMediaConstants.MIME_PNG;
        }
        if (isWebp(data)) {
            return AdminMediaConstants.MIME_WEBP;
        }
        if (isPdf(data)) {
            return AdminMediaConstants.MIME_PDF;
        }
        if (looksLikeSvgOrHtml(data)) {
            return null;
        }
        return null;
    }

    private static boolean isJpeg(byte[] data) {
        return data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A;
    }

    private static boolean isWebp(byte[] data) {
        if (data.length < 12) {
            return false;
        }
        return data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P';
    }

    private static boolean isPdf(byte[] data) {
        return data.length >= 5
                && data[0] == '%'
                && data[1] == 'P'
                && data[2] == 'D'
                && data[3] == 'F'
                && data[4] == '-';
    }

    private static boolean looksLikeSvgOrHtml(byte[] data) {
        int len = Math.min(data.length, 512);
        String head = new String(data, 0, len, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).trim();
        if (head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<html")) {
            return true;
        }
        return head.startsWith("<svg") || head.contains("<svg") || head.contains("image/svg+xml");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /** @return {@code true} 当对象 remove 成功（或可视为已不存在） */
    private boolean tryRemoveObject(String bucket, String objectKey) {
        try {
            objectStorageService.remove(bucket, objectKey);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Media object remove deferred bucket={} key={}", bucket, objectKey, ex);
            return false;
        }
    }

    private AdminMediaListItemDto toListItem(AdminMediaEntity entity) {
        return new AdminMediaListItemDto(
                entity.getId(),
                entity.getAgentId(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getExtension(),
                entity.getFileSize(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AdminMediaDetailDto toDetail(AdminMediaEntity entity) {
        return new AdminMediaDetailDto(
                entity.getId(),
                entity.getAgentId(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getExtension(),
                entity.getFileSize(),
                entity.getSha256(),
                entity.getVisibility(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
