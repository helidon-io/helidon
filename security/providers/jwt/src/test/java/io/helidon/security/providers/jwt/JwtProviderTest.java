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

package io.helidon.security.providers.jwt;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkEC;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkOctet;
import io.helidon.security.jwt.jwk.JwkRSA;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link JwtProvider}.
 */
public class JwtProviderTest {
    private static JwkKeys verifyKeys;

    @BeforeAll
    public static void initClass() {
        verifyKeys = JwkKeys.builder()
                .resource(Resource.create("verify-jwk.json"))
                .build();
    }

    @Test
    public void testEcBothWays() {
        String username = "user1";
        String userId = "user1-id";
        String email = "user1@example.org";
        String familyName = "Novak";
        String givenName = "Standa";
        String fullName = "Standa Novak";
        Locale locale = Locale.CANADA_FRENCH;

        Principal principal = Principal.builder()
                .name(username)
                .id(userId)
                .addAttribute("email", email)
                .addAttribute("email_verified", true)
                .addAttribute("family_name", familyName)
                .addAttribute("given_name", givenName)
                .addAttribute("full_name", fullName)
                .addAttribute("locale", locale)
                .build();

        Subject subject = Subject.create(principal);

        JwtProvider provider = JwtProvider.create(Config.create().get("security.providers.0.jwt"));

        SecurityContext context = Mockito.mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.of(subject));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/ec")
                .transport("http")
                .targetUri(URI.create("http://localhost:8080/ec"))
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat(provider.isOutboundSupported(request, outboundEnv, outboundEp), is(true));

        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        String signedToken = response.requestHeaders().get("Authorization").get(0);
        signedToken = signedToken.substring("bearer ".length());
        //now I want to validate it to prove it was correctly signed
        SignedJwt signedJwt = SignedJwt.parseToken(signedToken);
        signedJwt.verifySignature(verifyKeys).checkValid();
        Jwt jwt = signedJwt.getJwt();

        assertThat(jwt.subject(), is(Optional.of(userId)));
        assertThat(jwt.preferredUsername(), is(Optional.of(username)));
        assertThat(jwt.email(), is(Optional.of(email)));
        assertThat(jwt.emailVerified(), is(Optional.of(true)));
        assertThat(jwt.familyName(), is(Optional.of(familyName)));
        assertThat(jwt.givenName(), is(Optional.of(givenName)));
        assertThat(jwt.fullName(), is(Optional.of(fullName)));
        assertThat(jwt.locale(), is(Optional.of(locale)));
        assertThat(jwt.audience(), is(Optional.of(CollectionsHelper.listOf("audience.application.id"))));
        assertThat(jwt.issuer(), is(Optional.of("jwt.example.com")));
        assertThat(jwt.algorithm(), is(Optional.of(JwkEC.ALG_ES256)));
        Instant instant = jwt.issueTime().get();
        boolean compareResult = Instant.now().minusSeconds(10).compareTo(instant) < 0;
        assertThat("Issue time must not be older than 10 seconds", compareResult, is(true));
        Instant expectedNotBefore = instant.minus(5, ChronoUnit.SECONDS);
        assertThat(jwt.notBefore(), is(Optional.of(expectedNotBefore)));
        Instant expectedExpiry = instant.plus(60 * 60 * 24, ChronoUnit.SECONDS);
        assertThat(jwt.expirationTime(), is(Optional.of(expectedExpiry)));

        //now we need to use the same token to invoke authentication
        ProviderRequest atnRequest = mock(ProviderRequest.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + signedToken)
                .build();

        when(atnRequest.env()).thenReturn(se);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        OptionalHelper.from(authenticationResponse.user()
                                    .map(Subject::principal))
                .ifPresentOrElse(atnPrincipal -> {
                    assertThat(atnPrincipal.id(), is(userId));
                    assertThat(atnPrincipal.getName(), is(username));
                    assertThat(atnPrincipal.abacAttribute("email"), is(Optional.of(email)));
                    assertThat(atnPrincipal.abacAttribute("email_verified"), is(Optional.of(true)));
                    assertThat(atnPrincipal.abacAttribute("family_name"), is(Optional.of(familyName)));
                    assertThat(atnPrincipal.abacAttribute("given_name"), is(Optional.of(givenName)));
                    assertThat(atnPrincipal.abacAttribute("full_name"), is(Optional.of(fullName)));
                    assertThat(atnPrincipal.abacAttribute("locale"), is(Optional.of(locale)));
                }, () -> fail("User must be present in response"));
    }

    @Test
    public void testOctBothWays() {
        String userId = "user1-id";

        Principal tp = Principal.create(userId);
        Subject subject = Subject.create(tp);

        JwtProvider provider = JwtProvider.create(Config.create().get("security.providers.0.jwt"));

        SecurityContext context = Mockito.mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.of(subject));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/oct")
                .transport("http")
                .targetUri(URI.create("http://localhost:8080/oct"))
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat(provider.isOutboundSupported(request, outboundEnv, outboundEp), is(true));

        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        String signedToken = response.requestHeaders().get("Authorization").get(0);
        signedToken = signedToken.substring("bearer ".length());
        //now I want to validate it to prove it was correctly signed
        SignedJwt signedJwt = SignedJwt.parseToken(signedToken);
        signedJwt.verifySignature(verifyKeys).checkValid();
        Jwt jwt = signedJwt.getJwt();

        assertThat(jwt.subject(), is(Optional.of(userId)));
        assertThat(jwt.preferredUsername(), is(Optional.of(userId)));
        assertThat(jwt.email(), is(Optional.empty()));
        assertThat(jwt.emailVerified(), is(Optional.empty()));
        assertThat(jwt.familyName(), is(Optional.empty()));
        assertThat(jwt.givenName(), is(Optional.empty()));
        // stored as "name" attribute on principal, full name is stored as "name" in JWT
        assertThat(jwt.fullName(), is(Optional.empty()));
        assertThat(jwt.locale(), is(Optional.empty()));
        assertThat(jwt.audience(), is(Optional.of(CollectionsHelper.listOf("audience.application.id"))));
        assertThat(jwt.issuer(), is(Optional.of("jwt.example.com")));
        assertThat(jwt.algorithm(), is(Optional.of(JwkOctet.ALG_HS256)));
        Instant instant = jwt.issueTime().get();
        boolean compareResult = Instant.now().minusSeconds(10).compareTo(instant) < 0;
        assertThat("Issue time must not be older than 10 seconds", compareResult, is(true));
        Instant expectedNotBefore = instant.minus(5, ChronoUnit.SECONDS);
        assertThat(jwt.notBefore(), is(Optional.of(expectedNotBefore)));
        Instant expectedExpiry = instant.plus(60 * 60 * 24, ChronoUnit.SECONDS);
        assertThat(jwt.expirationTime(), is(Optional.of(expectedExpiry)));

        //now we need to use the same token to invoke authentication
        ProviderRequest atnRequest = mock(ProviderRequest.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + signedToken)
                .build();
        when(atnRequest.env()).thenReturn(se);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        OptionalHelper.from(authenticationResponse.user()
                                    .map(Subject::principal))
                .ifPresentOrElse(atnPrincipal -> {
                    assertThat(atnPrincipal.id(), is(userId));
                    assertThat(atnPrincipal.getName(), is(userId));
                    assertThat(atnPrincipal.abacAttribute("email"), is(Optional.empty()));
                    assertThat(atnPrincipal.abacAttribute("email_verified"), is(Optional.empty()));
                    assertThat(atnPrincipal.abacAttribute("family_name"), is(Optional.empty()));
                    assertThat(atnPrincipal.abacAttribute("given_name"), is(Optional.empty()));
                    assertThat(atnPrincipal.abacAttribute("full_name"), is(Optional.empty()));
                    assertThat(atnPrincipal.abacAttribute("locale"), is(Optional.empty()));
                }, () -> fail("User must be present in response"));

    }

    @Test
    public void testRsaBothWays() {
        String username = "user1";
        String userId = "user1-id";
        String email = "user1@example.org";
        String familyName = "Novak";
        String givenName = "Standa";
        String fullName = "Standa Novak";
        Locale locale = Locale.CANADA_FRENCH;

        Principal principal = Principal.builder()
                .name(username)
                .id(userId)
                .addAttribute("email", email)
                .addAttribute("email_verified", true)
                .addAttribute("family_name", familyName)
                .addAttribute("given_name", givenName)
                .addAttribute("full_name", fullName)
                .addAttribute("locale", locale)
                .build();

        Subject subject = Subject.create(principal);

        JwtProvider provider = JwtProvider.create(Config.create().get("security.providers.0.jwt"));

        SecurityContext context = Mockito.mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.of(subject));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/rsa")
                .transport("http")
                .targetUri(URI.create("http://localhost:8080/rsa"))
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat(provider.isOutboundSupported(request, outboundEnv, outboundEp), is(true));

        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        String signedToken = response.requestHeaders().get("Authorization").get(0);
        signedToken = signedToken.substring("bearer ".length());
        //now I want to validate it to prove it was correctly signed
        SignedJwt signedJwt = SignedJwt.parseToken(signedToken);
        signedJwt.verifySignature(verifyKeys).checkValid();
        Jwt jwt = signedJwt.getJwt();

        assertThat(jwt.subject(), is(Optional.of(userId)));
        assertThat(jwt.preferredUsername(), is(Optional.of(username)));
        assertThat(jwt.email(), is(Optional.of(email)));
        assertThat(jwt.emailVerified(), is(Optional.of(true)));
        assertThat(jwt.familyName(), is(Optional.of(familyName)));
        assertThat(jwt.givenName(), is(Optional.of(givenName)));
        assertThat(jwt.fullName(), is(Optional.of(fullName)));
        assertThat(jwt.locale(), is(Optional.of(locale)));
        assertThat(jwt.audience(), is(Optional.of(CollectionsHelper.listOf("audience.application.id"))));
        assertThat(jwt.issuer(), is(Optional.of("jwt.example.com")));
        assertThat(jwt.algorithm(), is(Optional.of(JwkRSA.ALG_RS256)));

        assertThat(jwt.issueTime(), is(not(Optional.empty())));
        jwt.issueTime()
                .ifPresent(instant -> {
                    boolean compareResult = Instant.now().minusSeconds(10).compareTo(instant) < 0;
                    assertThat("Issue time must not be older than 10 seconds", compareResult, is(true));
                    Instant expectedNotBefore = instant.minus(60, ChronoUnit.SECONDS);
                    assertThat(jwt.notBefore(), is(Optional.of(expectedNotBefore)));
                    Instant expectedExpiry = instant.plus(3600, ChronoUnit.SECONDS);
                    assertThat(jwt.expirationTime(), is(Optional.of(expectedExpiry)));
                });

        //now we need to use the same token to invoke authentication
        ProviderRequest atnRequest = mock(ProviderRequest.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + signedToken)
                .build();
        when(atnRequest.env()).thenReturn(se);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        OptionalHelper.from(authenticationResponse.user()
                                    .map(Subject::principal))
                .ifPresentOrElse(atnPrincipal -> {
                    assertThat(atnPrincipal.id(), is(userId));
                    assertThat(atnPrincipal.getName(), is(username));
                    assertThat(atnPrincipal.abacAttribute("email"), is(Optional.of(email)));
                    assertThat(atnPrincipal.abacAttribute("email_verified"), is(Optional.of(true)));
                    assertThat(atnPrincipal.abacAttribute("family_name"), is(Optional.of(familyName)));
                    assertThat(atnPrincipal.abacAttribute("given_name"), is(Optional.of(givenName)));
                    assertThat(atnPrincipal.abacAttribute("full_name"), is(Optional.of(fullName)));
                    assertThat(atnPrincipal.abacAttribute("locale"), is(Optional.of(locale)));
                }, () -> fail("User must be present in response"));
    }
}
