package com.agentto.rag.common.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {
    public static <T> PageResult<T> from(Page<T> value) {
        return new PageResult<>(value.getContent(), value.getTotalElements(), value.getNumber(), value.getSize(),
                value.getTotalPages());
    }
}
