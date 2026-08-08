package com.mywebsite.admin.constant;

import java.util.Map;
import java.util.Set;

/** Reservation 域常量：status 闭集、合法迁移与分页。 */
public final class AdminReservationConstants {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final Set<String> ALLOWED_STATUSES =
            Set.of(STATUS_NEW, STATUS_CONFIRMED, STATUS_DONE, STATUS_CANCELLED);

    public static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_DONE, STATUS_CANCELLED);

    /** 合法 status 迁移：NEW→CONFIRMED/CANCELLED；CONFIRMED→DONE/CANCELLED；终态不可再迁。 */
    public static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            STATUS_NEW, Set.of(STATUS_CONFIRMED, STATUS_CANCELLED),
            STATUS_CONFIRMED, Set.of(STATUS_DONE, STATUS_CANCELLED),
            STATUS_DONE, Set.of(),
            STATUS_CANCELLED, Set.of());

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private AdminReservationConstants() {}
}
