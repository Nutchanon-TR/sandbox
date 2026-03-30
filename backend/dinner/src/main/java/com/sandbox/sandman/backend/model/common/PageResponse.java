package com.sandbox.sandman.backend.model.common;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <T> PageResponse<T> toPageResponse(Page<T> page) {
        PageResponse<T> resp = new PageResponse<>();
        resp.setContent(page.getContent());
        resp.setPage(page.getNumber() + 1);
        resp.setSize(page.getSize());
        resp.setTotalElements(page.getTotalElements());
        resp.setTotalPages(page.getTotalPages());
        return resp;
    }
}
