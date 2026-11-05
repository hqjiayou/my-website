package com.mywebsite.admin.constant;

import java.util.Map;
import java.util.Set;

/** Media 域常量：MIME 白名单、状态、分页。 */
public final class AdminMediaConstants {

    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    /** 可见范围（media_file.scope，V59）：AGENT=仅来源站与引用站 / PLATFORM=平台可见。 */
    public static final String SCOPE_AGENT = "AGENT";

    public static final String SCOPE_PLATFORM = "PLATFORM";

    public static final Set<String> ALLOWED_SCOPES = Set.of(SCOPE_AGENT, SCOPE_PLATFORM);

    /** 列表视图（{@code view} 查询参数）：library=本站库（默认）/ platform=平台库。 */
    public static final String VIEW_LIBRARY = "library";

    public static final String VIEW_PLATFORM = "platform";

    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";
    public static final String MIME_WEBP = "image/webp";
    public static final String MIME_PDF = "application/pdf";

    public static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(MIME_JPEG, MIME_PNG, MIME_WEBP, MIME_PDF);

    public static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            MIME_JPEG, "jpg",
            MIME_PNG, "png",
            MIME_WEBP, "webp",
            MIME_PDF, "pdf");

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;

    private AdminMediaConstants() {}
}
