/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.integration.jersey;

import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.common.SecurityTracing;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link SecurityFilter}.
 */
class SecurityFilterTest {
    private static Security security;
    private static ServerConfig serverConfig;
    private static SecurityTracing tracing;

    @BeforeAll
    static void initClass() {
        security = Security.builder()
                .build();

        serverConfig = ResourceConfig.forApplication(getApplication());
        tracing = SecurityTracing.get();
    }

    @Test
    void testAtnAbortWith() {
        SecurityFeature feature = SecurityFeature.builder(security)
                .build();

        SecurityContext securityContext = security.createContext("testAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               serverConfig,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();
        filterContext.setJerseyRequest(request);

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);

        SecurityClientBuilder<AuthenticationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.buildAndGet()).thenReturn(AuthenticationResponse.failed("Unit-test"));

        sf.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing());
        assertThat(filterContext.isShouldFinish(), is(true));

        verify(request).abortWith(argThat(response -> response.getStatus() == 401));
    }

    @Test
    void testAtnThrowException() {
        SecurityFeature feature = SecurityFeature.builder(security)
                .useAbortWith(false)
                .build();

        SecurityContext securityContext = security.createContext("testNotAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               serverConfig,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();
        filterContext.setJerseyRequest(request);

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);

        SecurityClientBuilder<AuthenticationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.buildAndGet()).thenReturn(AuthenticationResponse.failed("Unit-test"));

        WebApplicationException e = Assertions.assertThrows(WebApplicationException.class, () ->
                sf.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing()));

        Response response = e.getResponse();
        String message = e.getMessage();

        assertThat(response.getStatus(), is(401));
        assertThat(message, is("Unit-test"));
    }

    @Test
    void testAtzAbortWith() {
        SecurityFeature feature = SecurityFeature.builder(security)
                .build();

        SecurityContext securityContext = security.createContext("testAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               serverConfig,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();
        filterContext.setJerseyRequest(request);

        SecurityClientBuilder<AuthorizationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.buildAndGet()).thenReturn(AuthorizationResponse.builder()
                                                             .description("Unit-test")
                                                             .status(SecurityResponse.SecurityStatus.FAILURE)
                                                             .build());

        sf.processAuthorization(filterContext, clientBuilder);
        assertThat(filterContext.isShouldFinish(), is(true));

        verify(request).abortWith(argThat(response -> response.getStatus() == 403));
    }

    @Test
    void testAtzThrowException() {
        SecurityFeature feature = SecurityFeature.builder(security)
                .useAbortWith(false)
                .build();

        SecurityContext securityContext = security.createContext("testNotAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               serverConfig,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();
        filterContext.setJerseyRequest(request);

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);

        SecurityClientBuilder<AuthorizationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.buildAndGet()).thenReturn(AuthorizationResponse.builder()
                                                             .description("Unit-test")
                                                             .status(SecurityResponse.SecurityStatus.FAILURE)
                                                             .build());

        WebApplicationException e = Assertions.assertThrows(WebApplicationException.class, () ->
                sf.processAuthorization(filterContext, clientBuilder));

        Response response = e.getResponse();
        String message = e.getMessage();

        assertThat(response.getStatus(), is(403));
        assertThat(message, is("Unit-test"));
    }

    private static Application getApplication() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return Set.of(OptionalSecurityTest.TheResource.class);
            }
        };
    }
}