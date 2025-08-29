package com.labs.repartitioner.constant;

public enum TopicEnum {
    UPPERCASE("uppercase"),
    UPPERCASE_STORAGE("uppercase-key-store"),
    UPPERCASE_TABLE("uppercase-table");
    private final String name;

    TopicEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
