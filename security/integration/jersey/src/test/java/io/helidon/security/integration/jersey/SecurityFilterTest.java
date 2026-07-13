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

package io.helidon.security.integration.jersey;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import io.helidon.common.http.HttpRequest;
import io.helidon.common.http.UriInfo;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.webserver.ServerRequest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
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
    private static final Logger LOGGER = Logger.getLogger(SecurityFilterTest.class.getName());

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
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());

        filter.doFilter(request, securityContext, mock(ServerRequest.class));

        assertThat(securityContext.env().headers().get(Security.HEADER_ORIG_URI),
                   is(List.of("/raw%2Fresource?return=https%3A%2F%2Fexample.com%2Ftest")));
    }

    @Test
    void testOriginalUriHeaderUsesRequestedUriPrefix() {
        SecurityContext securityContext = security.createContext("testOriginalUriHeaderUsesRequestedUriPrefix");
        SecurityFilterCommon filter = new TestSecurityFilter(security);
        ContainerRequest request = mock(ContainerRequest.class);
        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        URI requestUri = URI.create("http://example.org/v1/raw%2Fresource?return=https%3A%2F%2Fexample.com%2Ftest");
        UriInfo requestedUri = new UriInfo("https",
                                           "example.org",
                                           443,
                                           "/api/audit-mgmt/auditexemptions/v1/raw/resource",
                                           Optional.of("return=https%3A%2F%2Fexample.com%2Ftest"));
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

        headers.put("Host", List.of("example.org"));
        when(request.getMethod()).thenReturn("POST");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getProperty("io.helidon.jaxrs.requested-uri")).thenReturn(requestedUri);
        when(uriInfo.getRequestUri()).thenReturn(requestUri);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());

        filter.doFilter(request, securityContext, serverRequest("/v1/raw/resource"));

        assertThat(securityContext.env().headers().get(Security.HEADER_ORIG_URI),
                   is(List.of("/api/audit-mgmt/auditexemptions/v1/raw%2Fresource"
                                      + "?return=https%3A%2F%2Fexample.com%2Ftest")));
    }

    @Test
    void testOriginalUriHeaderUsesCanonicalRequestedUriPath() {
        assertOriginalUriHeader("http://example.org/v1/a/%2E%2E/raw%2Fresource/",
                                "/api/audit-mgmt/auditexemptions/v1/raw/resource",
                                "/v1/raw/resource",
                                Optional.empty(),
                                "/api/audit-mgmt/auditexemptions/v1/a/%2E%2E/raw%2Fresource/");
    }

    @Test
    void testOriginalUriHeaderUsesCanonicalRequestedUriPathWithLeadingParent() {
        assertOriginalUriHeader("http://example.org/%2E%2E/admin/secret",
                                "/api/admin/secret",
                                "/admin/secret",
                                Optional.empty(),
                                "/api/%2E%2E/admin/secret");
    }

    @Test
    void testOriginalUriHeaderUsesCanonicalRequestedUriPathWithLeadingSlash() {
        assertOriginalUriHeader("http://example.org/%2Fadmin/secret",
                                "/api/admin/secret",
                                "/admin/secret",
                                Optional.empty(),
                                "/api/%2Fadmin/secret");
    }

    @Test
    void testOriginalUriHeaderUsesMatrixParameterPath() {
        assertOriginalUriHeader("http://example.org/a%3Bx=1/%2E%2E/b",
                                "/a;x=1/../b",
                                "/a;x=1/../b",
                                Optional.empty(),
                                "/a%3Bx=1/%2E%2E/b");
    }

    @Test
    void testOriginalUriHeaderUsesRequestedUriPrefixWithMatrixParameterPath() {
        assertOriginalUriHeader("http://example.org/v1/a%2F/resource;v=1",
                                "/api/v1/a//resource;v=1",
                                "/v1/a//resource;v=1",
                                Optional.empty(),
                                "/api/v1/a%2F/resource;v=1");
    }

    @Test
    void testOriginalUriHeaderKeepsJerseyQueryAfterUriRewrite() {
        assertOriginalUriHeader("http://example.org/new?new=2",
                                "/legacy",
                                "/legacy",
                                Optional.of("old=1"),
                                "/new?new=2");
    }

    @Test
    void testOriginalUriHeaderKeepsJerseyQueryWithRequestedUriPrefix() {
        assertOriginalUriHeader("http://example.org/new?new=2",
                                "/api/new",
                                "/new",
                                Optional.of("old=1"),
                                "/api/new?new=2");
    }

    @Test
    void testOriginalUriHeaderUsesRequestedUriPrefixAfterPathRewrite() {
        assertOriginalUriHeader("http://example.org/resource?new=2",
                                "/api/v1/resource",
                                "/v1/resource",
                                Optional.of("old=1"),
                                "/api/resource?new=2");
    }

    private void assertOriginalUriHeader(String requestUriString,
                                         String requestedPath,
                                         String serverPath,
                                         Optional<String> requestedQuery,
                                         String expected) {
        SecurityContext securityContext = security.createContext("testOriginalUriHeaderUsesCanonicalRequestedUriPath");
        SecurityFilterCommon filter = new TestSecurityFilter(security);
        ContainerRequest request = mock(ContainerRequest.class);
        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        URI requestUri = URI.create(requestUriString);
        UriInfo requestedUri = new UriInfo("https", "example.org", 443, requestedPath, requestedQuery);
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("Host", List.of("example.org"));
        when(request.getMethod()).thenReturn("POST");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getProperty("io.helidon.jaxrs.requested-uri")).thenReturn(requestedUri);
        when(uriInfo.getRequestUri()).thenReturn(requestUri);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        filter.doFilter(request, securityContext, serverRequest(serverPath));
        assertThat(securityContext.env().headers().get(Security.HEADER_ORIG_URI),
                   is(List.of(expected)));
    }

    private static ServerRequest serverRequest(String path) {
        ServerRequest serverRequest = mock(ServerRequest.class);
        HttpRequest.Path requestPath = mock(HttpRequest.Path.class);
        when(serverRequest.path()).thenReturn(requestPath);
        when(requestPath.absolute()).thenReturn(requestPath);
        when(requestPath.toString()).thenReturn(path);
        return serverRequest;
    }

    private static Application getApplication() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return Set.of(OptionalSecurityTest.TheResource.class);
            }
        };
    }

    private static final class TestSecurityFilter extends SecurityFilterCommon {
        private TestSecurityFilter(Security security) {
            super(security, SecurityFeature.builder(security).build().featureConfig());
        }

        @Override
        protected FilterContext initRequestFiltering(ContainerRequestContext requestContext) {
            FilterContext context = new FilterContext();
            SecurityDefinition methodSecurity = mock(SecurityDefinition.class);
            when(methodSecurity.getSecurityLevels()).thenReturn(List.of());
            context.setMethodSecurity(methodSecurity);
            context.setResourceName("TestResource");

            return configureContext(context, requestContext, requestContext.getUriInfo());
        }

        @Override
        protected void processSecurity(ContainerRequestContext request,
                                       FilterContext context,
                                       SecurityTracing tracing,
                                       SecurityContext securityContext) {
        }

        @Override
        protected Logger logger() {
            return LOGGER;
        }
    }
}
