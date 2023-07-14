/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import java.util.Optional;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityResponse;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.integration.common.SecurityTracing;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for an issue (JC-304):
 * Example in microprofile: idcs/IdcsResource, method "getCurrentSubject" has authentication optional, yet it redirects even
 * when not logged in.
 * <br/>
 *
 * Current behavior: we are redirected to login page
 * Correct behavior: we should get an empty subject
 * <br/>
 *
 * This must be fixed in integration with Jersey/web server, when we receive a FINISH command, we should check if optional...
 */
@SuppressWarnings("unchecked")
class OptionalSecurityTest {
    private static SecurityFilter securityFilter;
    private static SecurityClientBuilder<AuthenticationResponse> clientBuilder;
    private static Security security;
    private static FeatureConfig featureConfig;
    private static SecurityTracing tracing;

    @BeforeAll
    static void init() {
        /*
         * Prepare parameters
         */
        security = Security.builder()
                .addAuthenticationProvider(OptionalSecurityTest::authenticate)
                .build();

        featureConfig = new FeatureConfig();

        AuthenticationResponse atr = AuthenticationResponse.builder()
                .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                .statusCode(301)
                .build();

        clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.build().submit()).thenReturn(atr);

        tracing = SecurityTracing.get();
    }

    @Test
    void testOptional() {
        SecurityContext securityContext = security.createContext("context_id");
        SecurityFilterContext filterContext = new SecurityFilterContext();

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);
        when(methodSecurity.authenticationOptional()).thenReturn(true);

        /*
         * Instantiate the filter
         */
        securityFilter = new SecurityFilter(featureConfig,
                                        security,
                                        securityContext);

        /*
         * The actual tested method
         */
        securityFilter.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing());

        assertThat(filterContext.isShouldFinish(), is(false));
        assertThat(securityContext.user(), is(Optional.empty()));
    }

    @Test
    void testNotOptional() {
        SecurityContext securityContext = security.createContext("context_id");
        SecurityFilterContext filterContext = new SecurityFilterContext();
        filterContext.setJerseyRequest(mock(ContainerRequest.class));
        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);
        when(methodSecurity.authenticationOptional()).thenReturn(false);

        /*
         * Instantiate the filter
         */
        securityFilter = new SecurityFilter(featureConfig,
                                        security,
                                        securityContext);
        /*
         * The actual tested method
         */
        securityFilter.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing());

        assertThat(filterContext.isShouldFinish(), is(true));
        assertThat(securityContext.user(), is(Optional.empty()));
    }

    private static AuthenticationResponse authenticate(ProviderRequest request) {
        return AuthenticationResponse.builder()
                .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                .statusCode(301)
                .build();
    }

    @Path("/")
    public static class TheResource {
        @GET
        @Authenticated
        public String getIt() {
            return "hello!";
        }
    }
}
