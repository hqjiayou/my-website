package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/** Todo 编排条目；status 闭集与 agent-services {@code TodoStatus} 一致。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentTodoItem(String id, String title, String status) {

    public static final Set<String> STATUSES = Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED");

    /** 与 agent-services 上限一致（§21.4）。 */
    public static final int MAX_ITEMS = 20;

    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_TITLE_LENGTH = 200;
}
