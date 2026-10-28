package com.mywebsite.agent.core.context;

import java.util.Optional;

public final class CurrentAgentContext {

    private static final ThreadLocal<AgentContext> HOLDER = new ThreadLocal<>();

    private CurrentAgentContext() {
    }

    public static void set(AgentContext context) {
        HOLDER.set(context);
    }

    public static Optional<AgentContext> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
