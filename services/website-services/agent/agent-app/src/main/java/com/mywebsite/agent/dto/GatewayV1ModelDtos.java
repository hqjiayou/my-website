package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** {@code GET /v1/models}（OpenAI 兼容形状）。{@code id} 是入口 alias，不暴露内部 modelSpec/agentId。 */
public final class GatewayV1ModelDtos {

    private GatewayV1ModelDtos() {}

    public record Model(String id, String object, long created, @JsonProperty("owned_by") String ownedBy) {
        public Model(String alias) {
            this(alias, "model", 0L, "my-website");
        }
    }

    public record ListResponse(String object, List<Model> data) {
        public ListResponse(List<Model> data) {
            this("list", data);
        }
    }
}
