package io.helidon.security.providers.oidc;

import io.helidon.security.jwt.SignedJwt;

public class OidcRefreshResult {
    private final boolean succeeded;
    private final SignedJwt accessToken;
    private final SignedJwt idToken;
    private final String refreshToken;
    private final String errorMessage;

    public OidcRefreshResult(boolean succeeded, SignedJwt accessToken, SignedJwt idToken, String refreshToken, String errorMessage) {
        this.succeeded = succeeded;
        this.accessToken = accessToken;
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.errorMessage = errorMessage;
    }

    public boolean succeeded() {
        return succeeded;
    }

    public SignedJwt getAccessToken() {
        return accessToken;
    }

    public SignedJwt getIdToken() {
        return idToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
