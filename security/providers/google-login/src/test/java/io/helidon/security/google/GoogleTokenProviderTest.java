/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.google;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.providers.TokenCredential;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.JsonFactory;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link GoogleTokenProvider}.
 */
public class GoogleTokenProviderTest {
    private static final String TOKEN_VALUE = "12345safdafa12354asdf24a4sdfasdfasdf";
    private static final String pictureUrl = "http://www.google.com";
    private static final String email = "test@google.com";
    private static final String familyName = "Novak";
    private static final String givenName = "Jarda";
    private static final String fullName = "Jarda Novak";
    private static final String name = "test@google.com";
    private static final String userId = "123456789";

    private static GoogleTokenProvider provider;

    @BeforeAll
    public static void initClass() throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setEmailVerified(true);
        payload.setSubject(userId);
        payload.set("name", fullName);
        payload.set("locale", Locale.US.toLanguageTag());
        payload.set("family_name", familyName);
        payload.set("given_name", givenName);
        payload.set("picture", pictureUrl);

        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(verifier.verify(TOKEN_VALUE)).thenReturn(googleIdToken);
        when(verifier.verify(googleIdToken)).thenReturn(true);

        BiFunction<JsonFactory, String, GoogleIdToken> parser = (jsonFactory, s) -> googleIdToken;

        provider = GoogleTokenProvider.builder()
                .clientId("clientId")
                .verifier(verifier)
                .tokenParser(parser)
                .build();
    }

    @Test
    public void testInbound() {
        ProviderRequest inboundRequest = createInboundRequest("Authorization", "bearer " + TOKEN_VALUE);
        AuthenticationResponse response = provider.syncAuthenticate(inboundRequest);
        assertThat(response.getUser(), is(not(Optional.empty())));
        response.getUser().ifPresent(subject -> {
            Principal principal = subject.getPrincipal();
            assertThat(principal.getName(), is(name));
            assertThat(principal.getId(), is(userId));
        });
    }

    @Test
    public void testInboundIncorrectToken() throws ExecutionException, InterruptedException {
        ProviderRequest inboundRequest = createInboundRequest("Authorization", "tearer " + TOKEN_VALUE);
        AuthenticationResponse response = provider.authenticate(inboundRequest).toCompletableFuture().get();

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getStatusCode().orElse(200), is(400));
        assertThat(response.getResponseHeaders().get("WWW-Authenticate"), notNullValue());
    }

    @Test
    public void testInboundMissingToken() throws ExecutionException, InterruptedException {
        ProviderRequest inboundRequest = createInboundRequest("OtherHeader", "tearer " + TOKEN_VALUE);
        AuthenticationResponse response = provider.authenticate(inboundRequest).toCompletableFuture().get();

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getStatusCode().orElse(200), is(401));
        assertThat(response.getResponseHeaders().get("WWW-Authenticate"), notNullValue());
    }

    @Test
    public void testInboundInvalidToken() throws ExecutionException, InterruptedException, GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(TOKEN_VALUE)).thenReturn(null);
        GoogleTokenProvider provider = GoogleTokenProvider.builder().clientId("clientId").verifier(verifier).build();

        ProviderRequest inboundRequest = createInboundRequest("Authorization", "bearer " + TOKEN_VALUE);
        AuthenticationResponse response = provider.authenticate(inboundRequest).toCompletableFuture().get();

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getStatusCode().orElse(200), is(401));
        assertThat(response.getResponseHeaders().get("WWW-Authenticate"), notNullValue());
    }

    @Test
    public void testInboundVerificationException()
            throws ExecutionException, InterruptedException, GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(TOKEN_VALUE)).thenThrow(new IOException("Failed to verify token"));
        GoogleTokenProvider provider = GoogleTokenProvider.builder().clientId("clientId").verifier(verifier).build();

        ProviderRequest inboundRequest = createInboundRequest("Authorization", "bearer " + TOKEN_VALUE);
        AuthenticationResponse response = provider.authenticate(inboundRequest).toCompletableFuture().get();

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getStatusCode().orElse(200), is(401));
        assertThat(response.getResponseHeaders().get("WWW-Authenticate"), notNullValue());
    }

    private ProviderRequest createInboundRequest(String headerName, String headerValue) {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .header(headerName, headerValue)
                .build();

        Span secSpan = GlobalTracer.get().buildSpan("security").start();

        SecurityContext context = mock(SecurityContext.class);
        when(context.getExecutorService()).thenReturn(ForkJoinPool.commonPool());
        when(context.getTracer()).thenReturn(GlobalTracer.get());
        when(context.getTracingSpan()).thenReturn(secSpan.context());

        ProviderRequest mock = mock(ProviderRequest.class);
        when(mock.getContext()).thenReturn(context);
        when(mock.getEnv()).thenReturn(env);

        return mock;
    }

    @Test
    public void testOutbound() {
        ProviderRequest outboundRequest = buildOutboundRequest();
        SecurityEnvironment outboundEnv = SecurityEnvironment.create();
        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat("Outbound should be supported",
                   provider.isOutboundSupported(outboundRequest, outboundEnv, outboundEp),
                   is(true));

        OutboundSecurityResponse response = provider.syncOutbound(outboundRequest, outboundEnv, outboundEp);

        List<String> authorization = response.getRequestHeaders().get("Authorization");

        assertThat(authorization, notNullValue());
        assertThat(authorization.size(), is(1));

        String header = authorization.get(0);
        assertThat(header.toLowerCase(), startsWith("bearer "));
        assertThat(header, endsWith(TOKEN_VALUE));
    }

    private ProviderRequest buildOutboundRequest() {
        TokenCredential tc = TokenCredential.create(TOKEN_VALUE, "accounts.google.com", Instant.now(), Instant.now());

        Subject subject = Subject.builder()
                .principal(Principal.create("test"))
                .addPublicCredential(tc)
                .build();

        SecurityContext context = mock(SecurityContext.class);
        when(context.getUser()).thenReturn(Optional.of(subject));
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getContext()).thenReturn(context);
        when(context.getExecutorService()).thenReturn(ForkJoinPool.commonPool());

        return request;
    }
}
