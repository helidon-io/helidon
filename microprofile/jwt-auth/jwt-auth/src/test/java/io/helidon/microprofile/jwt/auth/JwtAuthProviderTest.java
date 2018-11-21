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

package io.helidon.microprofile.jwt.auth;

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
import io.helidon.security.Role;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkEC;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkOctet;
import io.helidon.security.jwt.jwk.JwkRSA;

import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.jwt.Claims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link JwtAuthProvider}.
 */
public class JwtAuthProviderTest {
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
                .addAttribute("roles", CollectionsHelper.setOf("role1", "role2"))
                .build();

        Subject subject = Subject.builder()
                .principal(principal)
                .addGrant(Role.create("group1"))
                .addGrant(Role.create("group2"))
                .addGrant(Role.create("group3"))
                .build();

        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("security.providers.0.mp-jwt-auth"));

        SecurityContext context = Mockito.mock(SecurityContext.class);
        when(context.getUser()).thenReturn(Optional.of(subject));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getContext()).thenReturn(context);
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/ec")
                .transport("http")
                .targetUri(URI.create("http://localhost:8080/ec"))
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat(provider.isOutboundSupported(request, outboundEnv, outboundEp), is(true));

        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        String signedToken = response.getRequestHeaders().get("Authorization").get(0);
        signedToken = signedToken.substring("bearer ".length());
        //now I want to validate it to prove it was correctly signed
        SignedJwt signedJwt = SignedJwt.parseToken(signedToken);
        signedJwt.verifySignature(verifyKeys).checkValid();
        Jwt jwt = signedJwt.getJwt();

        // MP specific additions
        assertThat(jwt.getPayloadClaim("upn"), not(Optional.empty()));
        assertThat(jwt.getPayloadClaim("groups"), not(Optional.empty()));
        assertThat(jwt.getUserPrincipal(), is(Optional.of(username)));
        assertThat(jwt.getUserGroups(), not(Optional.empty()));
        assertThat(jwt.getUserGroups().get(), hasItems("group1", "group2", "group3"));
        // End of MP specific additions

        assertThat(jwt.getSubject(), is(Optional.of(userId)));
        assertThat(jwt.getPreferredUsername(), is(Optional.of(username)));
        assertThat(jwt.getEmail(), is(Optional.of(email)));
        assertThat(jwt.getEmailVerified(), is(Optional.of(true)));
        assertThat(jwt.getFamilyName(), is(Optional.of(familyName)));
        assertThat(jwt.getGivenName(), is(Optional.of(givenName)));
        assertThat(jwt.getFullName(), is(Optional.of(fullName)));
        assertThat(jwt.getLocale(), is(Optional.of(locale)));
        assertThat(jwt.getAudience(), is(Optional.of(CollectionsHelper.listOf("audience.application.id"))));
        assertThat(jwt.getIssuer(), is(Optional.of("jwt.example.com")));
        assertThat(jwt.getAlgorithm(), is(Optional.of(JwkEC.ALG_ES256)));
        Instant instant = jwt.getIssueTime().get();
        boolean compareResult = Instant.now().minusSeconds(10).compareTo(instant) < 0;
        assertThat("Issue time must not be older than 10 seconds", compareResult, is(true));
        Instant expectedNotBefore = instant.minus(5, ChronoUnit.SECONDS);
        assertThat(jwt.getNotBefore(), is(Optional.of(expectedNotBefore)));
        Instant expectedExpiry = instant.plus(60 * 60 * 24, ChronoUnit.SECONDS);
        assertThat(jwt.getExpirationTime(), is(Optional.of(expectedExpiry)));

        //now we need to use the same token to invoke authentication
        ProviderRequest atnRequest = mockRequest(signedToken);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        OptionalHelper.from(authenticationResponse.getUser()
                                    .map(Subject::getPrincipal))
                .ifPresentOrElse(atnPrincipal -> {
                    assertThat(atnPrincipal, instanceOf(JsonWebTokenImpl.class));
                    JsonWebTokenImpl jsonWebToken = (JsonWebTokenImpl) atnPrincipal;
                    String upn = jsonWebToken.getClaim(Claims.upn.name());
                    assertThat(upn, is(username));

                    assertThat(atnPrincipal.getId(), is(userId));
                    assertThat(atnPrincipal.getName(), is(username));
                    assertThat(atnPrincipal.getAttribute("email"), is(Optional.of(email)));
                    assertThat(atnPrincipal.getAttribute("email_verified"), is(Optional.of(true)));
                    assertThat(atnPrincipal.getAttribute("family_name"), is(Optional.of(familyName)));
                    assertThat(atnPrincipal.getAttribute("given_name"), is(Optional.of(givenName)));
                    assertThat(atnPrincipal.getAttribute("full_name"), is(Optional.of(fullName)));
                    assertThat(atnPrincipal.getAttribute("locale"), is(Optional.of(locale)));
                }, () -> fail("User must be present in response"));
    }

    @Test
    public void testOctBothWays() {
        String userId = "user1-id";

        Principal tp = Principal.create(userId);
        Subject subject = Subject.create(tp);

        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("security.providers.0.mp-jwt-auth"));

        SecurityContext context = Mockito.mock(SecurityContext.class);
        when(context.getUser()).thenReturn(Optional.of(subject));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getContext()).thenReturn(context);
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/oct")
                .transport("http")
                .targetUri(URI.create("http://localhost:8080/oct"))
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat(provider.isOutboundSupported(request, outboundEnv, outboundEp), is(true));

        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        String signedToken = response.getRequestHeaders().get("Authorization").get(0);
        signedToken = signedToken.substring("bearer ".length());
        //now I want to validate it to prove it was correctly signed
        SignedJwt signedJwt = SignedJwt.parseToken(signedToken);
        signedJwt.verifySignature(verifyKeys).checkValid();
        Jwt jwt = signedJwt.getJwt();

        assertThat(jwt.getSubject(), is(Optional.of(userId)));
        assertThat(jwt.getPreferredUsername(), is(Optional.of(userId)));
        assertThat(jwt.getEmail(), is(Optional.empty()));
        assertThat(jwt.getEmailVerified(), is(Optional.empty()));
        assertThat(jwt.getFamilyName(), is(Optional.empty()));
        assertThat(jwt.getGivenName(), is(Optional.empty()));
        // stored as "name" attribute on principal, full name is stored as "name" in JWT
        assertThat(jwt.getFullName(), is(Optional.empty()));
        assertThat(jwt.getLocale(), is(Optional.empty()));
        assertThat(jwt.getAudience(), is(Optional.of(CollectionsHelper.listOf("audience.application.id"))));
        assertThat(jwt.getIssuer(), is(Optional.of("jwt.example.com")));
        assertThat(jwt.getAlgorithm(), is(Optional.of(JwkOctet.ALG_HS256)));
        Instant instant = jwt.getIssueTime().get();
        boolean compareResult = Instant.now().minusSeconds(10).compareTo(instant) < 0;
        assertThat("Issue time must not be older than 10 seconds", compareResult, is(true));
        Instant expectedNotBefore = instant.minus(5, ChronoUnit.SECONDS);
        assertThat(jwt.getNotBefore(), is(Optional.of(expectedNotBefore)));
        Instant expectedExpiry = instant.plus(60 * 60 * 24, ChronoUnit.SECONDS);
        assertThat(jwt.getExpirationTime(), is(Optional.of(expectedExpiry)));

        //now we need to use the same token to invoke authentication
        ProviderRequest atnRequest = mockRequest(signedToken);


        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        OptionalHelper.from(authenticationResponse.getUser()
                                    .map(Subject::getPrincipal))
                .ifPresentOrElse(atnPrincipal -> {
                    assertThat(atnPrincipal.getId(), is(userId));
                    assertThat(atnPrincipal.getName(), is(userId));
                    assertThat(atnPrincipal.getAttribute("email"), is(Optional.empty()));
                    assertThat(atnPrincipal.getAttribute("email_verified"), is(Optional.empty()));
                    assertThat(atnPrincipal.getAttribute("family_name"), is(Optional.empty()));
                    assertThat(atnPrincipal.getAttribute("given_name"), is(Optional.empty()));
                    assertThat(atnPrincipal.getAttribute("full_name"), is(Optional.empty()));
                    assertThat(atnPrincipal.getAttribute("locale"), is(Optional.empty()));
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

        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("security.providers.0.mp-jwt-auth"));

        SecurityContext context = Mockito.mock(SecurityContext.class);
        when(context.getUser()).thenReturn(Optional.of(subject));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getContext()).thenReturn(context);
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/rsa")
                .transport("http")
                .targetUri(URI.create("http://localhost:8080/rsa"))
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat(provider.isOutboundSupported(request, outboundEnv, outboundEp), is(true));

        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        String signedToken = response.getRequestHeaders().get("Authorization").get(0);
        signedToken = signedToken.substring("bearer ".length());
        //now I want to validate it to prove it was correctly signed
        SignedJwt signedJwt = SignedJwt.parseToken(signedToken);
        signedJwt.verifySignature(verifyKeys).checkValid();
        Jwt jwt = signedJwt.getJwt();

        assertThat(jwt.getSubject(), is(Optional.of(userId)));
        assertThat(jwt.getPreferredUsername(), is(Optional.of(username)));
        assertThat(jwt.getEmail(), is(Optional.of(email)));
        assertThat(jwt.getEmailVerified(), is(Optional.of(true)));
        assertThat(jwt.getFamilyName(), is(Optional.of(familyName)));
        assertThat(jwt.getGivenName(), is(Optional.of(givenName)));
        assertThat(jwt.getFullName(), is(Optional.of(fullName)));
        assertThat(jwt.getLocale(), is(Optional.of(locale)));
        assertThat(jwt.getAudience(), is(Optional.of(CollectionsHelper.listOf("audience.application.id"))));
        assertThat(jwt.getIssuer(), is(Optional.of("jwt.example.com")));
        assertThat(jwt.getAlgorithm(), is(Optional.of(JwkRSA.ALG_RS256)));

        assertThat(jwt.getIssueTime(), is(not(Optional.empty())));
        jwt.getIssueTime()
                .ifPresent(instant -> {
                    boolean compareResult = Instant.now().minusSeconds(10).compareTo(instant) < 0;
                    assertThat("Issue time must not be older than 10 seconds", compareResult, is(true));
                    Instant expectedNotBefore = instant.minus(60, ChronoUnit.SECONDS);
                    assertThat(jwt.getNotBefore(), is(Optional.of(expectedNotBefore)));
                    Instant expectedExpiry = instant.plus(3600, ChronoUnit.SECONDS);
                    assertThat(jwt.getExpirationTime(), is(Optional.of(expectedExpiry)));
                });

        //now we need to use the same token to invoke authentication
        ProviderRequest atnRequest = mockRequest(signedToken);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        OptionalHelper.from(authenticationResponse.getUser()
                                    .map(Subject::getPrincipal))
                .ifPresentOrElse(atnPrincipal -> {
                    assertThat(atnPrincipal.getId(), is(userId));
                    assertThat(atnPrincipal.getName(), is(username));
                    assertThat(atnPrincipal.getAttribute("email"), is(Optional.of(email)));
                    assertThat(atnPrincipal.getAttribute("email_verified"), is(Optional.of(true)));
                    assertThat(atnPrincipal.getAttribute("family_name"), is(Optional.of(familyName)));
                    assertThat(atnPrincipal.getAttribute("given_name"), is(Optional.of(givenName)));
                    assertThat(atnPrincipal.getAttribute("full_name"), is(Optional.of(fullName)));
                    assertThat(atnPrincipal.getAttribute("locale"), is(Optional.of(locale)));
                }, () -> fail("User must be present in response"));
    }

    private ProviderRequest mockRequest(String signedToken) {
        ProviderRequest atnRequest = mock(ProviderRequest.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + signedToken)
                .build();
        when(atnRequest.getEnv()).thenReturn(se);

        EndpointConfig ep = mock(EndpointConfig.class);
        LoginConfig lc = mock(LoginConfig.class);
        when(lc.authMethod()).thenReturn(JwtAuthAnnotationAnalyzer.LOGIN_CONFIG_METHOD);
        when(lc.realmName()).thenReturn("");

        when(ep.combineAnnotations(LoginConfig.class, EndpointConfig.AnnotationScope.APPLICATION))
                .thenReturn(CollectionsHelper.listOf(lc));
        when(atnRequest.getEndpointConfig()).thenReturn(ep);

        return atnRequest;
    }
}
