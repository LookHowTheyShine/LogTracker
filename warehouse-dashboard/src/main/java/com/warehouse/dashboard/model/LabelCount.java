package com.warehouse.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LabelCount {
    private final String label;
    private final long count;
}
