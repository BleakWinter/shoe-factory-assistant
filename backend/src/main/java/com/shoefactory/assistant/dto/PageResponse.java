package com.shoefactory.assistant.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public class PageResponse<T> {

    // 统一分页结构，兼容前端表格的 records/total/page/size。
    private List<T> records;
    private long total;
    private long page;
    private long size;

    public static <T> PageResponse<T> from(IPage<?> page, List<T> records) {
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(page.getTotal());
        response.setPage(page.getCurrent());
        response.setSize(page.getSize());
        return response;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
