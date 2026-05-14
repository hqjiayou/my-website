package com.mywebsite.common.page;

import java.util.List;

public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final int pageNum;
    private final int pageSize;

    public PageResult(List<T> records, long total, int pageNum, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }
}
