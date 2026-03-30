package com.sandbox.sandman.backend.model.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortRequest {
    private String field;
    private String direction; // "asc" หรือ "desc"
}
