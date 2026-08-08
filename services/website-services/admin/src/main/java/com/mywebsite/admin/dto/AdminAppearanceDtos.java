package com.mywebsite.admin.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mywebsite.admin.constant.AdminAppearanceConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Admin Appearance HTTP 契约 DTO 聚合（schema v1 闭集）。 */
public final class AdminAppearanceDtos {

    private AdminAppearanceDtos() {}

    /** schema v1 配置；反序列化拒绝未知键（fail-closed）。 */
    @JsonDeserialize(using = AdminAppearanceConfigV1Deserializer.class)
    public record AdminAppearanceConfigV1Dto(
            @NotBlank @Pattern(regexp = AdminAppearanceConstants.PRIMARY_COLOR_PATTERN) String primaryColor,
            @NotBlank String fontStyle,
            @NotBlank String buttonStyle,
            @NotBlank String heroLayout) {}

    public record AdminAppearanceDetailDto(
            Integer schemaVersion,
            AdminAppearanceConfigV1Dto config,
            Integer version,
            OffsetDateTime updatedAt) {}

    public record AdminAppearancePutRequest(
            @NotNull Integer schemaVersion,
            @NotNull @Valid AdminAppearanceConfigV1Dto config,
            @NotNull Integer version) {}

    /** Jackson 闭集反序列化：仅允许 v1 四键，缺键/未知键均失败。 */
    public static final class AdminAppearanceConfigV1Deserializer
            extends JsonDeserializer<AdminAppearanceConfigV1Dto> {

        @Override
        public AdminAppearanceConfigV1Dto deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node == null || !node.isObject()) {
                throw JsonMappingException.from(p, "appearance config must be a JSON object");
            }
            Set<String> keys = new HashSet<>();
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                keys.add(it.next());
            }
            if (!keys.equals(AdminAppearanceConstants.CONFIG_KEYS_V1)) {
                throw JsonMappingException.from(
                        p, "appearance config keys must be exactly schema v1 closed set");
            }
            String primaryColor = requireText(node, "primaryColor", p);
            String fontStyle = requireText(node, "fontStyle", p);
            String buttonStyle = requireText(node, "buttonStyle", p);
            String heroLayout = requireText(node, "heroLayout", p);
            return new AdminAppearanceConfigV1Dto(primaryColor, fontStyle, buttonStyle, heroLayout);
        }

        private static String requireText(JsonNode node, String field, JsonParser p) throws JsonMappingException {
            JsonNode value = node.get(field);
            if (value == null || !value.isTextual() || value.asText().isBlank()) {
                throw JsonMappingException.from(p, "appearance config." + field + " is required");
            }
            return value.asText();
        }
    }
}
