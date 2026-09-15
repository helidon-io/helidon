/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.security.providers.google.login;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleTokenProviderErrorTest {
    private static final String INTERNAL_DETAIL = "internal-proxy.example:8443\",error=\"injected\r\nX-Detail: secret";
    private static final String INVALID_REQUEST_CHALLENGE = "Bearer realm=\"test-realm\",error=\"invalid_request\","
            + "error_description=\"Invalid authorization header\",scope=\"openid profile email\"";
    private static final String INVALID_TOKEN_CHALLENGE = "Bearer realm=\"test-realm\",error=\"invalid_token\","
            + "error_description=\"Token verification failed\",scope=\"openid profile email\"";

    @Test
    void verifierIOExceptionHasGenericDescription() throws GeneralSecurityException, IOException {
        IOException failure = new IOException(INTERNAL_DETAIL);
        GoogleTokenProvider provider = verifierFailure(failure).build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Bearer header.payload.signature")
                .build());

        assertFailure(response, 400, "Invalid authorization header", INVALID_REQUEST_CHALLENGE);
        assertThat(response.throwable().orElseThrow(), sameInstance(failure));
    }

    @Test
    void verifierSecurityExceptionHasGenericDescription() throws GeneralSecurityException, IOException {
        GeneralSecurityException failure = new GeneralSecurityException(INTERNAL_DETAIL);
        GoogleTokenProvider provider = verifierFailure(failure).build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Bearer header.payload.signature")
                .build());

        assertFailure(response, 401, "Token verification failed", INVALID_TOKEN_CHALLENGE);
        assertThat(response.throwable().orElseThrow(), sameInstance(failure));
    }

    @Test
    void malformedTokenDoesNotExposeExceptionClass() {
        GoogleTokenProvider provider = GoogleTokenProvider.builder()
                .clientId("clientId")
                .realm("test-realm")
                .verifier(mock(GoogleIdTokenVerifier.class))
                .build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Bearer not-a-jwt")
                .build());

        assertFailure(response, 401, "Token verification failed", INVALID_TOKEN_CHALLENGE);
        Throwable failure = response.throwable().orElseThrow();
        assertThat(failure, instanceOf(IllegalArgumentException.class));
        assertThat(failure.getMessage(), nullValue());
    }

    @Test
    void invalidClaimDoesNotExposeParserDiagnostic() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String token = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString("{\"exp\":\"private-claim-value\"}".getBytes(StandardCharsets.UTF_8))
                + ".AA";
        GoogleTokenProvider provider = GoogleTokenProvider.builder()
                .clientId("clientId")
                .realm("test-realm")
                .verifier(mock(GoogleIdTokenVerifier.class))
                .build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Bearer " + token)
                .build());

        assertFailure(response, 401, "Token verification failed", INVALID_TOKEN_CHALLENGE);
        assertThat(response.throwable().orElseThrow(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void invalidAuthorizationHeaderHasGenericDescription() {
        GoogleTokenProvider provider = GoogleTokenProvider.builder()
                .clientId("clientId")
                .realm("test-realm")
                .verifier(mock(GoogleIdTokenVerifier.class))
                .build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Basic private-credential")
                .build());

        assertFailure(response, 400, "Invalid authorization header", INVALID_REQUEST_CHALLENGE);
        assertThat(response.throwable().orElseThrow(), instanceOf(SecurityException.class));
    }

    @Test
    void rejectedTokenHasGenericDescription() {
        GoogleIdToken token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(new GoogleIdToken.Payload());
        GoogleTokenProvider provider = GoogleTokenProvider.builder()
                .clientId("clientId")
                .realm("test-realm")
                .verifier(mock(GoogleIdTokenVerifier.class))
                .tokenParser((jsonFactory, rawToken) -> token)
                .build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Bearer header.payload.signature")
                .build());

        assertFailure(response, 401, "Token verification failed", INVALID_TOKEN_CHALLENGE);
        assertThat(response.throwable(), is(Optional.empty()));
    }

    @Test
    void missingTokenChallengeHasNoError() {
        GoogleTokenProvider provider = GoogleTokenProvider.builder()
                .clientId("clientId")
                .realm("test-realm")
                .verifier(mock(GoogleIdTokenVerifier.class))
                .build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.create());

        assertFailure(response, 401, "Missing authorization header",
                      "Bearer realm=\"test-realm\",scope=\"openid profile email\"");
        assertThat(response.throwable(), is(Optional.empty()));
    }

    @Test
    void optionalProviderAbstainsOnInvalidRequest() throws GeneralSecurityException, IOException {
        GoogleTokenProvider provider = verifierFailure(new IOException(INTERNAL_DETAIL)).optional(true).build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Bearer header.payload.signature")
                .build());

        assertAbstain(response);
    }

    @Test
    void optionalProviderAbstainsOnInvalidToken() throws GeneralSecurityException, IOException {
        GoogleTokenProvider provider = verifierFailure(new GeneralSecurityException(INTERNAL_DETAIL)).optional(true).build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.builder()
                .header("Authorization", "Bearer header.payload.signature")
                .build());

        assertAbstain(response);
    }

    @Test
    void optionalProviderAbstainsOnMissingToken() {
        GoogleTokenProvider provider = GoogleTokenProvider.builder()
                .clientId("clientId")
                .realm("test-realm")
                .optional(true)
                .verifier(mock(GoogleIdTokenVerifier.class))
                .build();

        AuthenticationResponse response = authenticate(provider, SecurityEnvironment.create());

        assertAbstain(response);
    }

    private static GoogleTokenProvider.Builder verifierFailure(Exception failure) throws GeneralSecurityException, IOException {
        GoogleIdToken token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(new GoogleIdToken.Payload());
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(token)).thenThrow(failure);

        return GoogleTokenProvider.builder()
                .clientId("clientId")
                .realm("test-realm")
                .verifier(verifier)
                .tokenParser((jsonFactory, rawToken) -> token);
    }

    private static AuthenticationResponse authenticate(GoogleTokenProvider provider, SecurityEnvironment environment) {
        Tracer tracer = Tracer.global();
        Span span = tracer.spanBuilder("google-token-error-test").start();
        try {
            SecurityContext context = mock(SecurityContext.class);
            when(context.tracer()).thenReturn(tracer);
            when(context.tracingSpan()).thenReturn(span.context());
            ProviderRequest request = mock(ProviderRequest.class);
            when(request.securityContext()).thenReturn(context);
            when(request.env()).thenReturn(environment);
            return provider.authenticate(request);
        } finally {
            span.end();
        }
    }

    private static void assertFailure(AuthenticationResponse response, int statusCode, String description, String challenge) {
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode(), is(OptionalInt.of(statusCode)));
        assertThat(response.description(), is(Optional.of(description)));
        assertThat(response.responseHeaders(), is(Map.of("WWW-Authenticate", List.of(challenge))));
    }

    private static void assertAbstain(AuthenticationResponse response) {
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        assertThat(response.statusCode(), is(OptionalInt.empty()));
        assertThat(response.description(), is(Optional.empty()));
        assertThat(response.responseHeaders(), is(Map.of()));
        assertThat(response.throwable(), is(Optional.empty()));
    }
}
