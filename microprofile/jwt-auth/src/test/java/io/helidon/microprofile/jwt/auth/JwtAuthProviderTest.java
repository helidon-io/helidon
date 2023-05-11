/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityResponse;
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
    private static final String WRONG_TOKEN =
            "yJ4NXQjUzI1NiI6IlZjeXl1TVdxSGp4UjRVNmYzOTV3YmhUZXNZRmFaWXFSbDdBbUxjZE5sNXciLCJ4NXQiOiJTdEZFTlFaM2NMNndQaHFxODZnVmJTTG54TkUiLCJraWQiOiJTSUdOSU5HX0tFWSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJIU01BcHAtY2xpZW50X0FQUElEIiwidXNlci50ZW5hbnQubmFtZSI6ImlkY3MtNzNmYTNlZDY5ZTgxNDFhN2I5MDFmYWY3Zjg3M2U3OGUiLCJzdWJfbWFwcGluZ2F0dHIiOiJ1c2VyTmFtZSIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS5vcmFjbGVjbG91ZC5jb21cLyIsInRva190eXBlIjoiQVQiLCJjbGllbnRfaWQiOiJIU01BcHAtY2xpZW50X0FQUElEIiwiYXVkIjoiaHR0cDpcL1wvc2NhMDBjangudXMub3JhY2xlLmNvbTo3Nzc3Iiwic3ViX3R5cGUiOiJjbGllbnQiLCJzY29wZSI6InVybjpvcGM6cmVzb3VyY2U6Y29uc3VtZXI6OmFsbCIsImNsaWVudF90ZW5hbnRuYW1lIjoiaWRjcy03M2ZhM2VkNjllODE0MWE3YjkwMWZhZjdmODczZTc4ZSIsImV4cCI6MTU1MDU5NTk0MiwiaWF0IjoxNTUwNTA5NTQyLCJ0ZW5hbnRfaXNzIjoiaHR0cHM6XC9cL2lkY3MtNzNmYTNlZDY5ZTgxNDFhN2I5MDFmYWY3Zjg3M2U3OGUuaWRlbnRpdHkuYzlkZXYxLm9jOXFhZGV2LmNvbSIsImNsaWVudF9ndWlkIjoiN2JmZDM3MjM1ZGY3NDVjNDg5ZjYxZDM1ZTYzZGQ4ZmUiLCJjbGllbnRfbmFtZSI6IkhTTUFwcC1jbGllbnQiLCJ0ZW5hbnQiOiJpZGNzLTczZmEzZWQ2OWU4MTQxYTdiOTAxZmFmN2Y4NzNlNzhlIiwianRpIjoiYzRkNjlhZjUtOGQ4OC00N2Q2LTkzMDctN2RjMmI3NWY4MDQyIn0.ZsngUzzso_sW6rMg3jB-lueiC2sknIDRlgvjumMjp5rRSdLux2X4XZIm2Oa15JbcrnC6I4sgqB0xU1Wte-TW4hbBDLFhaJKYKiNaHBE0L7J73ZK7ITg7dORKkyjLrofGt0m8Rse1OlE9AWevz-l27gtQMO_mctGfHri2BxiMbSN1HwOjWW3kGoqPgCJZJfh2TiFlocEpsXDH4qB1qwhuIoT91gw3kIJlQov0_a9uGEepMU_RWMRjVZCIvuV2hPq_mdeWy2IhkHPxq422CLZ9MDOfbv8F6dY6DralCH4mmKbGM3dbqpZokWQxXG7LG9vWX1PFWw0N9clYHJ4QqBJ4pA";

    private static JwkKeys verifyKeys;

    @BeforeAll
    public static void initClass() {
        verifyKeys = JwkKeys.builder()
                .resource(Resource.create("verify-jwk.json"))
                .build();
    }

    @Test
    public void testWrongToken() {
        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("security.providers.0.mp-jwt-auth"));

        //now we need to use the same token to invoke authentication
        ProviderRequest atnRequest = mock(ProviderRequest.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + WRONG_TOKEN)
                .build();
        EndpointConfig ec = mock(EndpointConfig.class);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        when(ec.securityLevels()).thenReturn(securityLevels);
        when(appSecurityLevel.filterAnnotations(LoginConfig.class, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(new LoginConfig() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return LoginConfig.class;
                    }

                    @Override
                    public String authMethod() {
                        return JwtAuthAnnotationAnalyzer.LOGIN_CONFIG_METHOD;
                    }

                    @Override
                    public String realmName() {
                        return "helidon-realm";
                    }
                }));

        when(atnRequest.env()).thenReturn(se);
        when(atnRequest.endpointConfig()).thenReturn(ec);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);

        assertThat(authenticationResponse.service(), is(Optional.empty()));
        assertThat(authenticationResponse.user(), is(Optional.empty()));
        assertThat(authenticationResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
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
                .addAttribute("roles", Set.of("role1", "role2"))
                .build();

        Subject subject = Subject.builder()
                .principal(principal)
                .addGrant(Role.create("group1"))
                .addGrant(Role.create("group2"))
                .addGrant(Role.create("group3"))
                .build();

        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("security.providers.0.mp-jwt-auth"));

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

        // MP specific additions
        assertThat(jwt.payloadClaim("upn"), not(Optional.empty()));
        assertThat(jwt.payloadClaim("groups"), not(Optional.empty()));
        assertThat(jwt.userPrincipal(), is(Optional.of(username)));
        assertThat(jwt.userGroups(), not(Optional.empty()));
        assertThat(jwt.userGroups().get(), hasItems("group1", "group2", "group3"));
        // End of MP specific additions

        assertThat(jwt.subject(), is(Optional.of(userId)));
        assertThat(jwt.preferredUsername(), is(Optional.of(username)));
        assertThat(jwt.email(), is(Optional.of(email)));
        assertThat(jwt.emailVerified(), is(Optional.of(true)));
        assertThat(jwt.familyName(), is(Optional.of(familyName)));
        assertThat(jwt.givenName(), is(Optional.of(givenName)));
        assertThat(jwt.fullName(), is(Optional.of(fullName)));
        assertThat(jwt.locale(), is(Optional.of(locale)));
        assertThat(jwt.audience(), is(Optional.of(List.of("audience.application.id"))));
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
        ProviderRequest atnRequest = mockRequest(signedToken);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        authenticationResponse.user()
                .map(Subject::principal)
                .ifPresentOrElse(atnPrincipal -> {
                    assertThat(atnPrincipal, instanceOf(JsonWebTokenImpl.class));
                    JsonWebTokenImpl jsonWebToken = (JsonWebTokenImpl) atnPrincipal;
                    String upn = jsonWebToken.getClaim(Claims.upn.name());
                    assertThat(upn, is(username));

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
    public void testOutboundNotSupportedWithoutConfiguration() {
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
                .addAttribute("roles", Set.of("role1", "role2"))
                .build();

        Subject subject = Subject.builder()
                .principal(principal)
                .addGrant(Role.create("group1"))
                .addGrant(Role.create("group2"))
                .addGrant(Role.create("group3"))
                .build();

        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("jwt-no-outbound"));

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

        assertThat(provider.isOutboundSupported(request, outboundEnv, outboundEp), is(false));
    }

    @Test
    public void testOctBothWays() {
        String userId = "user1-id";

        Principal tp = Principal.create(userId);
        Subject subject = Subject.create(tp);

        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("security.providers.0.mp-jwt-auth"));

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
        assertThat(jwt.audience(), is(Optional.of(List.of("audience.application.id"))));
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
        ProviderRequest atnRequest = mockRequest(signedToken);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        authenticationResponse.user()
                .map(Subject::principal)
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

        JwtAuthProvider provider = JwtAuthProvider.create(Config.create().get("security.providers.0.mp-jwt-auth"));

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
        assertThat(jwt.audience(), is(Optional.of(List.of("audience.application.id"))));
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
        ProviderRequest atnRequest = mockRequest(signedToken);

        AuthenticationResponse authenticationResponse = provider.syncAuthenticate(atnRequest);
        authenticationResponse.user()
                .map(Subject::principal)
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

    private ProviderRequest mockRequest(String signedToken) {
        ProviderRequest atnRequest = mock(ProviderRequest.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + signedToken)
                .build();
        when(atnRequest.env()).thenReturn(se);

        EndpointConfig ep = mock(EndpointConfig.class);
        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);

        LoginConfig lc = mock(LoginConfig.class);
        when(lc.authMethod()).thenReturn(JwtAuthAnnotationAnalyzer.LOGIN_CONFIG_METHOD);
        when(lc.realmName()).thenReturn("");

        when(ep.securityLevels()).thenReturn(securityLevels);
        when(appSecurityLevel.filterAnnotations(LoginConfig.class, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(lc));
        when(atnRequest.endpointConfig()).thenReturn(ep);

        return atnRequest;
    }
}
