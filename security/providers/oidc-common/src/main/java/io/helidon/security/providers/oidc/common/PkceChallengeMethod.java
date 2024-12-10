package io.helidon.security.providers.oidc.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.function.Function;

import io.helidon.common.crypto.HashDigest;

/**
 * PKCE challenge generation type.
 * Based on <a href="https://datatracker.ietf.org/doc/html/rfc7636#section-4.2">RFC7636 - Section 4.2</a>
 */
public enum PkceChallengeMethod {

    /**
     * No hashing will be applied. Challenge string will be the same as verifier.
     */
    PLAIN( "plain", it -> it),

    /**
     * SHA-256 algorithm is used to hash the verifier value.
     */
    S256("S256", it -> {
        try {
            byte[] asciiBytes = it.getBytes(StandardCharsets.US_ASCII);
            byte[] digest = MessageDigest.getInstance(HashDigest.ALGORITHM_SHA_256).digest(asciiBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

    private final String method;
    private final Function<String, String> transformation;

    PkceChallengeMethod(String method, Function<String, String> transformation) {
        this.method = method;
        this.transformation = transformation;
    }

    /**
     * Transform PKCE verifier to the challenge.
     *
     * @param verifier PKCE verifier
     * @return PKCE challenge
     */
    public String transform(String verifier) {
        return transformation.apply(verifier);
    }

    /**
     * PKCE method name.
     *
     * @return method name
     */
    public String method() {
        return method;
    }

}
