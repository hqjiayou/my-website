package com.mywebsite.agent.client;

import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;

/**
 * Python {@code DIMENSION_CHANGE_REQUIRED}（HTTP 409）。
 * 上层读取 {@link #dimensions()} 与 {@link #columnDimensions()}，不要当成普通 500。
 */
public class AgentDimensionChangeRequiredException extends BusinessException {

    private final int dimensions;
    private final Integer columnDimensions;

    public AgentDimensionChangeRequiredException(int dimensions, Integer columnDimensions) {
        super(ErrorCode.CONFLICT);
        this.dimensions = dimensions;
        this.columnDimensions = columnDimensions;
    }

    public int dimensions() {
        return dimensions;
    }

    public Integer columnDimensions() {
        return columnDimensions;
    }
}
