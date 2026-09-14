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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
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
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpEntryPoint;
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
        HttpEntryPoint.Interceptor.Chain chain = mock(HttpEntryPoint.Interceptor.Chain.class);
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
        verify(chain).proceed(request, response);
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
}
