package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class AdminGatewayAccessKeyDtos {

    private AdminGatewayAccessKeyDtos() {}

    /** 不含 keyHash 与明文。 */
    public record AdminGatewayAccessKeyDto(
            Long id,
            String name,
            String keyHint,
            OffsetDateTime expiresAt,
            OffsetDateTime revokedAt,
            OffsetDateTime createdAt,
            Integer version) {}

    public record AdminGatewayAccessKeyIssueRequest(
            @NotBlank @Size(max = 128) String name, OffsetDateTime expiresAt) {}

    /** 明文 {@code apiKey} 仅在签发响应中出现一次，之后不可再取回。 */
    public record AdminGatewayAccessKeyIssueResponse(
            Long id,
            String name,
            String keyHint,
            OffsetDateTime expiresAt,
            OffsetDateTime revokedAt,
            OffsetDateTime createdAt,
            Integer version,
            String apiKey) {}
}
