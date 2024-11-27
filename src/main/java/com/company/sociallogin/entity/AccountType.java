package com.company.sociallogin.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum AccountType implements EnumClass<String> {

    GOOGLE("google"),
    GITHUB("github");

    private final String id;

    AccountType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static AccountType fromId(String id) {
        for (AccountType at : AccountType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}