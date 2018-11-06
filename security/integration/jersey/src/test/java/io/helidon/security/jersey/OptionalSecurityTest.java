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

package io.helidon.security.jersey;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;

import io.helidon.common.CollectionsHelper;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityResponse;
import io.helidon.security.annot.Authenticated;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for an issue (JC-304):
 * Example in microprofile: idcs/IdcsResource, method "getCurrentSubject" has authentication optional, yet it redirects even
 * when not logged in.
 *
 * Current behavior: we are redirected to login page
 * Correct behavior: we should get an empty subject
 *
 * This must be fixed in integration with Jersey/web server, when we receive a FINISH command, we should check if optional...
 */
class OptionalSecurityTest {
    private static SecurityFilter secuFilter;
    private static SecurityClientBuilder<AuthenticationResponse> clientBuilder;
    private static Security security;
    private static FeatureConfig featureConfig;
    private static ResourceConfig serverConfig;
    private static ResourceInfo resourceInfo;
    private static UriInfo uriInfo;

    @BeforeAll
    static void init() {
        /*
         * Prepare parameters
         */
        security = Security.builder()
                .addAuthenticationProvider(OptionalSecurityTest::authenticate)
                .build();

        featureConfig = new FeatureConfig();

        serverConfig = ResourceConfig.forApplication(getApplication());

        resourceInfo = mock(ResourceInfo.class);
        doReturn(TheResource.class).when(resourceInfo).getResourceClass();

        uriInfo = mock(UriInfo.class);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());



        AuthenticationResponse atr = AuthenticationResponse.builder()
                .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                .statusCode(301)
                .build();

        clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.buildAndGet()).thenReturn(atr);
    }

    @Test
    void testOptional() {
        SecurityContext secuContext = security.createContext("context_id");
        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);
        when(methodSecurity.authenticationOptional()).thenReturn(true);

        /*
         * Instantiate the filter
         */
        secuFilter = new SecurityFilter(featureConfig,
                                        security,
                                        serverConfig,
                                        resourceInfo,
                                        uriInfo,
                                        secuContext);

        /*
         * The actual tested method
         */
        secuFilter.processAuthentication(filterContext, clientBuilder, methodSecurity);

        assertThat(filterContext.isShouldFinish(), is(false));
        assertThat(secuContext.getUser(), is(Optional.empty()));
    }

    @Test
    void testNotOptional() {
        SecurityContext secuContext = security.createContext("context_id");
        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();
        filterContext.setJerseyRequest(mock(ContainerRequest.class));
        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);
        when(methodSecurity.authenticationOptional()).thenReturn(false);

        /*
         * Instantiate the filter
         */
        secuFilter = new SecurityFilter(featureConfig,
                                        security,
                                        serverConfig,
                                        resourceInfo,
                                        uriInfo,
                                        secuContext);
        /*
         * The actual tested method
         */
        secuFilter.processAuthentication(filterContext, clientBuilder, methodSecurity);

        assertThat(filterContext.isShouldFinish(), is(true));
        assertThat(secuContext.getUser(), is(Optional.empty()));
    }

    private static Application getApplication() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return CollectionsHelper.setOf(TheResource.class);
            }
        };
    }

    private static CompletionStage<AuthenticationResponse> authenticate(ProviderRequest request) {
        AuthenticationResponse res = AuthenticationResponse.builder()
                .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                .statusCode(301)
                .build();

        return CompletableFuture.completedFuture(res);
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
