/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.List;
import java.util.Optional;

import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.common.SecurityTracing;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
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
@SuppressWarnings("unchecked")
class SecurityFilterTest {
    private static Security security;
    private static SecurityTracing tracing;

    @BeforeAll
    static void initClass() {
        security = Security.builder().build();

        tracing = SecurityTracing.get();
    }

    @Test
    void testAtnAbortWith() {
        JerseySecurityFeature feature = JerseySecurityFeature.builder(security).build();

        SecurityContext securityContext = security.createContext("testAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilterContext filterContext = new SecurityFilterContext();
        filterContext.jerseyRequest(request);

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);

        SecurityClientBuilder<AuthenticationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.submit()).thenReturn(AuthenticationResponse.failed("Unit-test"));

        sf.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing());
        assertThat(filterContext.shouldFinish(), is(true));

        verify(request).abortWith(argThat(response -> response.getStatus() == 401));
    }

    @Test
    void testAtnThrowException() {
        JerseySecurityFeature feature = JerseySecurityFeature.builder(security)
                .useAbortWith(false)
                .build();

        SecurityContext securityContext = security.createContext("testNotAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilterContext filterContext = new SecurityFilterContext();
        filterContext.jerseyRequest(request);

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);

        SecurityClientBuilder<AuthenticationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.submit()).thenReturn(AuthenticationResponse.failed("Unit-test"));

        WebApplicationException e = Assertions.assertThrows(WebApplicationException.class, () ->
                sf.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing()));

        Response response = e.getResponse();
        String message = e.getMessage();

        assertThat(response.getStatus(), is(401));
        assertThat(message, is("Unit-test"));
    }

    @Test
    void testAtzAbortWith() {
        JerseySecurityFeature feature = JerseySecurityFeature.builder(security)
                .build();

        SecurityContext securityContext = security.createContext("testAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilterContext filterContext = new SecurityFilterContext();
        filterContext.jerseyRequest(request);

        SecurityClientBuilder<AuthorizationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.submit()).thenReturn(AuthorizationResponse.builder()
                                                             .description("Unit-test")
                                                             .status(SecurityResponse.SecurityStatus.FAILURE)
                                                             .build());

        sf.processAuthorization(filterContext, clientBuilder);
        assertThat(filterContext.shouldFinish(), is(true));

        verify(request).abortWith(argThat(response -> response.getStatus() == 403));
    }

    @Test
    void testAtzThrowException() {
        JerseySecurityFeature feature = JerseySecurityFeature.builder(security)
                .useAbortWith(false)
                .build();

        SecurityContext securityContext = security.createContext("testNotAbortWith");

        SecurityFilter sf = new SecurityFilter(feature.featureConfig(),
                                               security,
                                               securityContext);

        ContainerRequest request = mock(ContainerRequest.class);

        SecurityFilterContext filterContext = new SecurityFilterContext();
        filterContext.jerseyRequest(request);

        SecurityClientBuilder<AuthorizationResponse> clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.submit()).thenReturn(AuthorizationResponse.builder()
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

    @Test
    void testBoundaryRequestTargetValues() {
        SecurityContext securityContext = security.createContext("testBoundaryRequestTarget");
        SecurityFilterCommon filter = new TestSecurityFilter(security);
        ContainerRequest request = mock(ContainerRequest.class);
        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        URI requestUri = URI.create("http://example.org/raw%2Fresource?");
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

        headers.put("Host", List.of("example.org"));
        when(request.getMethod()).thenReturn("POST");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaders()).thenReturn(headers);
        when(uriInfo.getRequestUri()).thenReturn(requestUri);

        filter.doFilter(request, securityContext);

        SecurityEnvironment env = securityContext.env();
        assertThat(env.requestedMethod(), is("POST"));
        assertThat(env.requestedPath().rawPath(), is("/raw%2Fresource"));
        assertThat(env.requestedQuery().isPresent(), is(true));
        assertThat(env.requestedQuery().orElseThrow().rawValue(), is(""));
    }

    @Test
    void testOriginalUriHeaderUsesRawPathAndQuery() {
        SecurityContext securityContext = security.createContext("testOriginalUriHeaderUsesRawPathAndQuery");
        SecurityFilterCommon filter = new TestSecurityFilter(security);
        ContainerRequest request = mock(ContainerRequest.class);
        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        URI requestUri = URI.create("http://example.org/raw%2Fresource?return=https%3A%2F%2Fexample.com%2Ftest");
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

        headers.put("Host", List.of("example.org"));
        when(request.getMethod()).thenReturn("POST");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaders()).thenReturn(headers);
        when(uriInfo.getRequestUri()).thenReturn(requestUri);

        filter.doFilter(request, securityContext);

        assertThat(securityContext.env().headers().get(Security.HEADER_ORIG_URI),
                   is(List.of("/raw%2Fresource?return=https%3A%2F%2Fexample.com%2Ftest")));
    }

    @Test
    void testExistingBoundaryRequestTargetValuesArePreserved() {
        SecurityContext securityContext = security.createContext("testExistingBoundaryRequestTarget");
        securityContext.env(SecurityEnvironment.builder()
                                    .targetUri(URI.create("http://example.org/raw%2Fresource?"))
                                    .method("POST")
                                    .path("/raw/resource")
                                    .requestedMethod("POST")
                                    .requestedPath(UriPath.create("/raw%2Fresource"))
                                    .requestedQuery(Optional.of(UriQuery.empty()))
                                    .build());
        SecurityFilterCommon filter = new TestSecurityFilter(security);
        ContainerRequest request = mock(ContainerRequest.class);
        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        URI requestUri = URI.create("http://example.org/raw/resource");
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

        headers.put("Host", List.of("example.org"));
        when(request.getMethod()).thenReturn("POST");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaders()).thenReturn(headers);
        when(uriInfo.getRequestUri()).thenReturn(requestUri);

        filter.doFilter(request, securityContext);

        SecurityEnvironment env = securityContext.env();
        assertThat(env.requestedMethod(), is("POST"));
        assertThat(env.requestedPath().rawPath(), is("/raw%2Fresource"));
        assertThat(env.requestedQuery().isPresent(), is(true));
        assertThat(env.requestedQuery().orElseThrow().rawValue(), is(""));
    }

    private static final class TestSecurityFilter extends SecurityFilterCommon {
        private static final System.Logger LOGGER = System.getLogger(TestSecurityFilter.class.getName());

        private TestSecurityFilter(Security security) {
            super(security, new FeatureConfig());
        }

        @Override
        protected System.Logger logger() {
            return LOGGER;
        }

        @Override
        protected void processSecurity(ContainerRequestContext request,
                                       SecurityFilterContext context,
                                       SecurityTracing tracing,
                                       SecurityContext securityContext) {
        }

        @Override
        protected SecurityFilterContext initRequestFiltering(ContainerRequestContext requestContext) {
            SecurityFilterContext context = new SecurityFilterContext();
            SecurityDefinition methodDef = new SecurityDefinition(false, false);

            context.methodSecurity(methodDef);
            context.resourceName("jax-rs");
            return configureContext(context, requestContext, requestContext.getUriInfo());
        }
    }
}
