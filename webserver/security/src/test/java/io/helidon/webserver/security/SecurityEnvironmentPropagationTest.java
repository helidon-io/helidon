/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.spi.AuditProvider;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityEnvironmentPropagationTest {
    private static final TypeName AUDITED = TypeName.create("io.helidon.security.annotations.Audited");
    private static final TypeName AUTHORIZED = TypeName.create("io.helidon.security.annotations.Authorized");
    private static final String RAW_BOUNDARY_PATH = "/raw%2Fresource";
    private static final String DECODED_BOUNDARY_PATH = "/raw/resource";

    @Test
    void testSecurityContextFilterStoresBoundaryRequestValues() {
        Context context = Context.create();
        HttpPrologue prologue = prologue(Method.POST, RAW_BOUNDARY_PATH + "?");
        RoutingRequest request = request(context, prologue, prologue.query(), DECODED_BOUNDARY_PATH);
        FilterChain chain = mock(FilterChain.class);

        Security security = Security.builder().build();
        SecurityContextFilter filter = new SecurityContextFilter(security, SecurityHandler.create());

        filter.filter(chain, request, mock(RoutingResponse.class));

        SecurityEnvironment env = context.get(SecurityContext.class)
                .orElseThrow()
                .env();
        assertThat(env.requestedMethod(), is("POST"));
        assertThat(env.requestedPath().rawPath(), is(RAW_BOUNDARY_PATH));
        assertThat(env.requestedQuery().isPresent(), is(true));
        assertThat(env.requestedQuery().orElseThrow().rawValue(), is(""));
        verify(chain).proceed();
    }

    @Test
    void testFilterCapturedBoundaryRequestValuesReachHttpSecurityInterceptor() throws Exception {
        AtomicReference<SecurityEnvironment> authorizedEnv = new AtomicReference<>();
        Security security = Security.builder()
                .addAuthorizationProvider(providerRequest -> {
                    authorizedEnv.set(providerRequest.env());
                    return AuthorizationResponse.permit();
                })
                .build();
        Context context = Context.create();
        HttpPrologue boundaryPrologue = prologue(Method.POST, RAW_BOUNDARY_PATH + "?");
        RoutingRequest boundaryRequest = request(context,
                                                 boundaryPrologue,
                                                 boundaryPrologue.query(),
                                                 DECODED_BOUNDARY_PATH);
        SecurityContextFilter filter = new SecurityContextFilter(security, SecurityHandler.create());

        filter.filter(mock(FilterChain.class), boundaryRequest, mock(RoutingResponse.class));

        HttpPrologue currentPrologue = prologue(Method.GET, "/rerouted?changed=true");
        RoutingRequest request = request(context, currentPrologue, UriQuery.create("changed=true"), "/rerouted");
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<Object[]> chainArgs = new AtomicReference<>();
        Interception.Interceptor.Chain<Void> chain = args -> {
            chainArgs.set(args);
            return null;
        };
        HttpSecurityInterceptor interceptor = new HttpSecurityInterceptor(security,
                                                                         Config.empty(),
                                                                         List.of(),
                                                                         List.of());
        InterceptionContext interceptionContext = mock(InterceptionContext.class);
        ServiceInfo serviceInfo = mock(ServiceInfo.class);
        TypedElementInfo elementInfo = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("test")
                .typeName(TypeNames.STRING)
                .addAnnotation(Annotation.create(AUTHORIZED))
                .build();

        when(serviceInfo.serviceType()).thenReturn(TypeName.create(SecurityEnvironmentPropagationTest.class));
        when(interceptionContext.serviceInfo()).thenReturn(serviceInfo);
        when(interceptionContext.elementInfo()).thenReturn(elementInfo);
        when(interceptionContext.typeAnnotations()).thenReturn(List.of());

        interceptor.proceed(interceptionContext, chain, request, response);

        SecurityEnvironment env = authorizedEnv.get();
        assertThat(env.method(), is("GET"));
        assertThat(env.path().orElseThrow(), is("/rerouted"));
        assertThat(env.queryParams().rawValue(), is("changed=true"));
        assertThat(env.requestedMethod(), is("POST"));
        assertThat(env.requestedPath().rawPath(), is(RAW_BOUNDARY_PATH));
        assertThat(env.requestedQuery().isPresent(), is(true));
        assertThat(env.requestedQuery().orElseThrow().rawValue(), is(""));
        assertThat(chainArgs.get()[0], is(request));
        assertThat(chainArgs.get()[1], is(response));
    }

    @Test
    void testActiveContextReachesGenericSecurityInterceptor() throws Exception {
        AtomicReference<SecurityEnvironment> authorizedEnv = new AtomicReference<>();
        Security security = Security.builder()
                .addAuthorizationProvider(providerRequest -> {
                    authorizedEnv.set(providerRequest.env());
                    return AuthorizationResponse.permit();
                })
                .build();
        SecurityContext securityContext = security.contextBuilder("graphql")
                .env(SecurityEnvironment.builder(security.serverTime())
                             .method("POST")
                             .path("/graphql")
                             .targetUri(URI.create("http://localhost/graphql"))
                             .build())
                .build();
        Context context = Context.create();
        context.register(securityContext);
        HttpSecurityInterceptor interceptor = new HttpSecurityInterceptor(security,
                                                                         Config.empty(),
                                                                         List.of(),
                                                                         List.of());
        InterceptionContext interceptionContext = interceptionContext(SecurityEnvironmentPropagationTest.class,
                                                                      "securedResolver",
                                                                      Annotation.create(AUTHORIZED));

        String result = Contexts.runInContextWithThrow(context,
                                                       () -> interceptor.proceed(interceptionContext,
                                                                                 _ -> "actual",
                                                                                 "environment"));

        assertThat(result, is("actual"));
        SecurityEnvironment env = authorizedEnv.get();
        assertThat(env.method(), is("POST"));
        assertThat(env.path().orElseThrow(), is("/graphql"));
        assertThat(env.abacAttribute("resourceType").orElseThrow(), is(SecurityEnvironmentPropagationTest.class.getName()));
        assertThat(securityContext.env().path().orElseThrow(), is("/graphql"));
    }

    @Test
    void testGenericExplicitAuthorizationIsScopedToEntryPoint() throws Exception {
        Security security = Security.builder()
                .addAuthorizationProvider(_ -> AuthorizationResponse.permit())
                .build();
        SecurityContext securityContext = security.contextBuilder("graphql")
                .env(SecurityEnvironment.builder(security.serverTime())
                             .method("POST")
                             .path("/graphql")
                             .targetUri(URI.create("http://localhost/graphql"))
                             .build())
                .build();
        Context context = Context.create();
        context.register(securityContext);
        HttpSecurityInterceptor interceptor = new HttpSecurityInterceptor(security,
                                                                         Config.empty(),
                                                                         List.of(),
                                                                         List.of());
        InterceptionContext implicitAuthorization = interceptionContext(SecurityEnvironmentPropagationTest.class,
                                                                        "implicitAuthorization",
                                                                        Annotation.create(AUTHORIZED));
        InterceptionContext explicitAuthorization = interceptionContext(SecurityEnvironmentPropagationTest.class,
                                                                        "explicitAuthorization",
                                                                        Annotation.create(AUTHORIZED,
                                                                                          Map.of("explicit", true)));

        Contexts.runInContextWithThrow(context,
                                       () -> interceptor.proceed(implicitAuthorization,
                                                                 _ -> "implicit",
                                                                 "environment"));

        assertThat(securityContext.isAuthorized(), is(true));
        SecurityException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                SecurityException.class,
                () -> Contexts.runInContextWithThrow(context,
                                                     () -> interceptor.proceed(explicitAuthorization,
                                                                               _ -> "secret",
                                                                               "environment")));
        assertThat(thrown.getMessage(), is("Security did not allow this request to proceed"));
        assertThat(context.get(SecurityContext.class).orElseThrow(), is(securityContext));
    }

    @Test
    void testGenericSecurityContextRestoredWhenAuditFails() {
        Security security = Security.builder()
                .addAuditProvider((AuditProvider) () -> event -> {
                    if ("request".equals(event.eventType())) {
                        throw new IllegalStateException("audit failed");
                    }
                })
                .build();
        SecurityContext securityContext = security.contextBuilder("graphql")
                .env(SecurityEnvironment.builder(security.serverTime())
                             .method("POST")
                             .path("/graphql")
                             .targetUri(URI.create("http://localhost/graphql"))
                             .build())
                .build();
        Context context = Context.create();
        context.register(securityContext);
        HttpSecurityInterceptor interceptor = new HttpSecurityInterceptor(security,
                                                                         Config.empty(),
                                                                         List.of(),
                                                                         List.of());
        InterceptionContext interceptionContext = interceptionContext(SecurityEnvironmentPropagationTest.class,
                                                                      "auditedResolver",
                                                                      Annotation.create(AUDITED));

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> Contexts.runInContextWithThrow(context,
                                                     () -> interceptor.proceed(interceptionContext,
                                                                               _ -> "actual",
                                                                               "environment")));

        assertThat(thrown.getMessage(), is("audit failed"));
        assertThat(securityContext.env().abacAttribute("entryPoint").isPresent(), is(false));
        assertThat(context.get(SecurityContext.class).orElseThrow(), is(securityContext));
    }

    private static RoutingRequest request(Context context,
                                          HttpPrologue prologue,
                                          UriQuery query,
                                          String routedPathValue) {
        RoutingRequest request = mock(RoutingRequest.class);
        RoutedPath routedPath = mock(RoutedPath.class);
        PeerInfo remotePeer = mock(PeerInfo.class);
        WritableHeaders<?> headers = WritableHeaders.create();

        headers.set(HeaderNames.HOST, "example.org");
        when(routedPath.path()).thenReturn(routedPathValue);
        when(routedPath.absolute()).thenReturn(routedPath);
        when(remotePeer.host()).thenReturn("127.0.0.1");
        when(remotePeer.port()).thenReturn(8080);
        when(request.context()).thenReturn(context);
        when(request.prologue()).thenReturn(prologue);
        when(request.path()).thenReturn(routedPath);
        when(request.requestedUri()).thenReturn(UriInfo.builder()
                                                     .scheme("http")
                                                     .host("example.org")
                                                     .port(80)
                                                     .path(UriPath.create(routedPathValue))
                                                     .query(query)
                                                     .build());
        when(request.query()).thenReturn(query);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(headers));
        when(request.remotePeer()).thenReturn(remotePeer);
        when(request.isSecure()).thenReturn(false);
        return request;
    }

    private static HttpPrologue prologue(Method method, String path) {
        return HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", method, path, true);
    }

    private static InterceptionContext interceptionContext(Class<?> serviceType,
                                                           String methodName,
                                                           Annotation... annotations) {
        InterceptionContext interceptionContext = mock(InterceptionContext.class);
        ServiceInfo serviceInfo = mock(ServiceInfo.class);
        TypedElementInfo.Builder elementInfoBuilder = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(methodName)
                .typeName(TypeNames.STRING);
        for (Annotation annotation : annotations) {
            elementInfoBuilder.addAnnotation(annotation);
        }
        TypedElementInfo elementInfo = elementInfoBuilder.build();

        when(serviceInfo.serviceType()).thenReturn(TypeName.create(serviceType));
        when(interceptionContext.serviceInfo()).thenReturn(serviceInfo);
        when(interceptionContext.elementInfo()).thenReturn(elementInfo);
        when(interceptionContext.typeAnnotations()).thenReturn(List.of());
        return interceptionContext;
    }
}
