package com.terratier.custommining.config;

import com.terratier.custommining.model.ToolType;

public record ToolRule(String id, Double speed, ToolType type, int tier) {
}
