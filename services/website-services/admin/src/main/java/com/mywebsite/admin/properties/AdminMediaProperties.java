package com.mywebsite.admin.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin.media")
public class AdminMediaProperties {

    /** 图片（jpeg/png/webp）上限，默认 5MiB。 */
    private long maxImageBytes = 5L * 1024 * 1024;

    /** PDF 上限，默认 20MiB。 */
    private long maxPdfBytes = 20L * 1024 * 1024;

    /** 预览 presigned URL 有效期。 */
    private Duration presignTtl = Duration.ofMinutes(10);

    /** 软删后保留多久再硬清对象。 */
    private Duration orphanRetention = Duration.ofHours(1);

    /** UPLOADING/FAILED 超时后进入 orphan 清理。 */
    private Duration orphanStaleUpload = Duration.ofHours(1);

    private int orphanBatchSize = 50;

    /** orphan 定时任务间隔（毫秒）。 */
    private long orphanFixedDelayMs = 900_000L;

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxPdfBytes() {
        return maxPdfBytes;
    }

    public void setMaxPdfBytes(long maxPdfBytes) {
        this.maxPdfBytes = maxPdfBytes;
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    public void setPresignTtl(Duration presignTtl) {
        this.presignTtl = presignTtl;
    }

    public Duration getOrphanRetention() {
        return orphanRetention;
    }

    public void setOrphanRetention(Duration orphanRetention) {
        this.orphanRetention = orphanRetention;
    }

    public Duration getOrphanStaleUpload() {
        return orphanStaleUpload;
    }

    public void setOrphanStaleUpload(Duration orphanStaleUpload) {
        this.orphanStaleUpload = orphanStaleUpload;
    }

    public int getOrphanBatchSize() {
        return orphanBatchSize;
    }

    public void setOrphanBatchSize(int orphanBatchSize) {
        this.orphanBatchSize = orphanBatchSize;
    }

    public long getOrphanFixedDelayMs() {
        return orphanFixedDelayMs;
    }

    public void setOrphanFixedDelayMs(long orphanFixedDelayMs) {
        this.orphanFixedDelayMs = orphanFixedDelayMs;
    }
}
