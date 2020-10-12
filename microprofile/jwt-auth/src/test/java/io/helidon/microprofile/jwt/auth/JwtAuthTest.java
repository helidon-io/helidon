/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.annotation.security.PermitAll;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.JsonString;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import io.helidon.config.Config;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;

import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link io.helidon.microprofile.jwt.auth.JsonWebTokenProducer}.
 */
@HelidonTest
@AddBean(JwtAuthTest.MyApp.class)
@AddBean(JwtAuthTest.MyResource.class)
@AddBean(JwtAuthTest.ResourceWithPublicMethod.class)
class JwtAuthTest {
    @Inject
    private WebTarget target;

    @Test
    void testRsa() {
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

        io.helidon.security.SecurityContext context = Mockito.mock(io.helidon.security.SecurityContext.class);
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

        // authenticated
        String httpResponse = target.path("/hello")
                .request()
                .header("Authorization", signedToken)
                .get(String.class);

        assertThat(httpResponse, is("Hello user1"));

        httpResponse = target.path("/public")
                .path("/hello")
                .request()
                .header("Authorization", signedToken)
                .get(String.class);

        assertThat(httpResponse, is("Hello user1"));
    }

    @Test
    void testPublicEndpoint() {
        // public
        String httpResponse = target.path("/public")
                .request()
                .get(String.class);

        assertThat(httpResponse, is("Hello World"));
    }

    @LoginConfig(authMethod = "MP-JWT", realmName = "Helidon")
    public static class MyApp extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(MyResource.class, ResourceWithPublicMethod.class);
        }
    }

    @Path("/")
    @RequestScoped
    public static class MyResource {
        @Inject
        private JsonWebToken callerPrincipal;
        @Context
        private SecurityContext securityContext;

        @Inject
        @Claim(standard = Claims.iss)
        private String issuer;

        @Inject
        @Claim(standard = Claims.exp)
        private long expirationPrimitive;

        @Inject
        @Claim(standard = Claims.exp)
        private Long expiration;

        @Inject
        @Claim(standard = Claims.email_verified)
        private Boolean emailVerified;

        @Inject
        @Claim(standard = Claims.email_verified)
        private boolean emailVerifiedPrimitive;

        @Inject
        @Claim("iss")
        private JsonString issuerJson;

        @Inject
        @Claim(standard = Claims.aud)
        private ClaimValue<Optional<Set<String>>> audience;

        @Path("/hello")
        @GET
        public String hello() {
            return "Hello " + (null == callerPrincipal ? "Unknown" : callerPrincipal.getName());
        }

    }

    @Path("/public")
    @RequestScoped
    public static class ResourceWithPublicMethod {
        @Inject
        private JsonWebToken callerPrincipal;

        @Context
        private SecurityContext securityContext;

        @Inject
        @Claim(standard = Claims.iss)
        private String issuer;

        @Inject
        @Claim(standard = Claims.exp)
        private Long expiration;

        @Inject
        @Claim(standard = Claims.email_verified)
        private Boolean emailVerified;

        @Inject
        @Claim("iss")
        private JsonString issuerJson;

        @Inject
        @Claim(standard = Claims.aud)
        private ClaimValue<Optional<Set<String>>> audience;

        @Path("/hello")
        @GET
        public String hello() {
            return "Hello " + (null == callerPrincipal ? "Unknown" : callerPrincipal.getName());
        }

        @PermitAll
        @GET
        public String noAuth() {
            return "Hello World";
        }
    }
}
