package com.mywebsite.site.core.context;

import java.util.Optional;

public final class CurrentSiteContext {

    private static final ThreadLocal<SiteContext> HOLDER = new ThreadLocal<>();

    private CurrentSiteContext() {
    }

    public static void set(SiteContext context) {
        HOLDER.set(context);
    }

    public static Optional<SiteContext> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
