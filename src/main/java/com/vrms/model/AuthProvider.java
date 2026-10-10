package com.vrms.model;

/** How an account was created: email + password, or OAuth2 sign-in with an identity provider. */
public enum AuthProvider {
    LOCAL, GOOGLE, GITHUB
}
