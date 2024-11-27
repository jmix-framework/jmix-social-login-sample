package com.company.sociallogin.security;

public class UnknownAuthProviderException extends RuntimeException {
    public UnknownAuthProviderException(String unknownOAuth2Provider) {
        super("Unknown OAuth2 provider: " + unknownOAuth2Provider);
    }
}
