package com.company.sociallogin.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum AuthenticationType implements EnumClass<String> {

    APP("APP"),
    GOOGLE("GOOGLE"),
    GITHUB("GITHUB");

    private final String id;

    AuthenticationType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static AuthenticationType fromId(String id) {
        for (AuthenticationType at : AuthenticationType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}