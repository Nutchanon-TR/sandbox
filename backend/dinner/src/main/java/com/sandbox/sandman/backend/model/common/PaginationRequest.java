package com.sandbox.sandman.backend.model.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaginationRequest {
    @Builder.Default
    @Max(Integer.MAX_VALUE-1)
    @Min(1)
    private int page = 1;

    @Builder.Default
    @Max(50)
    @Min(1)
    private int size = 10;

    @Builder.Default
    private List<SortRequest> sort = List.of(new SortRequest("id", "asc"));

    public Pageable toPageable() {
        Sort sortObj = Sort.unsorted();
        if (sort != null && !sort.isEmpty()) {
            sortObj = Sort.by(
                    sort.stream()
                            .map(s -> new Sort.Order(
                                    Sort.Direction.fromString(s.getDirection()), s.getField())
                            ).toList()
            );
        }
        return PageRequest.of(page - 1, size, sortObj);
    }
}