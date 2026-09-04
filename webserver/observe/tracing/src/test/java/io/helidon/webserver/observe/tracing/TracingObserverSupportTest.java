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

package io.helidon.webserver.observe.tracing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.Context;
import io.helidon.common.uri.UriPath;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider;
import io.helidon.webserver.spi.ServerFeature;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@Testing.Test(perMethod = true)
class TracingObserverSupportTest {
    @Test
    void defaultTracerComesFromServiceRegistry() {
        Tracer tracer = mock(Tracer.class);
        Services.set(Tracer.class, tracer);

        TracingObserver observer = TracingObserver.create(builder -> {
        });

        assertThat("Tracing observer tracer", observer.prototype().tracer(), sameInstance(tracer));
    }

    @Test
    void defaultTracerFallsBackToNoOpTracer() {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .discoverServicesFromServiceLoader(false)
                                                                              .build());
        try {
            Services.registry(manager.registry());

            TracingObserver observer = TracingObserver.create(builder -> {
            });

            assertThat("Tracing observer tracer enabled",
                       observer.prototype().tracer().enabled(),
                       is(false));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void managedObserveProviderUsesOwningRegistryDependencies() {
        Tracer globalTracer = mock(Tracer.class);
        Tracer owningTracer = mock(Tracer.class);
        TracingSemanticConventionsProvider globalSemantics = mock(TracingSemanticConventionsProvider.class);
        TracingSemanticConventionsProvider owningSemantics = mock(TracingSemanticConventionsProvider.class);
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        AtomicInteger semanticsSupplierCalls = new AtomicInteger();

        Services.set(Tracer.class, globalTracer);
        Services.set(TracingSemanticConventionsProvider.class, globalSemantics);
        when(serviceRegistry.get(Tracer.class)).thenReturn(owningTracer);
        when(serviceRegistry.supply(TracingSemanticConventionsProvider.class))
                .thenReturn(() -> {
                    semanticsSupplierCalls.incrementAndGet();
                    return owningSemantics;
                });

        Observer observer = new TracingObserveProvider().create(Config.empty(),
                                                                "test",
                                                                serviceRegistry);

        assertThat("Tracing provider observer type", observer.type(), is("tracing"));
        assertThat("Tracing provider tracer",
                   ((TracingObserver) observer).prototype().tracer(),
                   sameInstance(owningTracer));

        Filter filter = registerFilter((TracingObserver) observer);

        assertThat("Semantic conventions supplier calls", semanticsSupplierCalls.get(), is(1));

        invoke(filter, owningTracer, owningSemantics);

        verify(owningSemantics).create(any(), anyString(), any(), any());
        verify(globalTracer, never()).extract(any());
        verifyZeroInteractions(globalSemantics);
    }

    @Test
    void disabledManagedObserverDoesNotResolveSemanticConventions() {
        Tracer tracer = mock(Tracer.class);
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        AtomicInteger semanticsSupplierCalls = new AtomicInteger();
        when(serviceRegistry.get(Tracer.class)).thenReturn(tracer);
        when(serviceRegistry.supply(TracingSemanticConventionsProvider.class))
                .thenReturn(() -> {
                    semanticsSupplierCalls.incrementAndGet();
                    return mock(TracingSemanticConventionsProvider.class);
                });
        Observer observer = new TracingObserveProvider().create(config(Map.of("enabled", "false")),
                                                                "test",
                                                                serviceRegistry);
        ServerFeature.ServerFeatureContext featureContext = mock(ServerFeature.ServerFeatureContext.class);

        observer.register(featureContext, List.of(), endpoint -> endpoint);

        assertThat("Semantic conventions supplier calls", semanticsSupplierCalls.get(), is(0));
        verifyZeroInteractions(featureContext);
    }

    @Test
    void directObserveProviderUsesGlobalRegistryDependencies() {
        Tracer tracer = mock(Tracer.class);
        TracingSemanticConventionsProvider semanticConventionsProvider = mock(TracingSemanticConventionsProvider.class);
        Services.set(Tracer.class, tracer);
        Services.set(TracingSemanticConventionsProvider.class, semanticConventionsProvider);

        Observer observer = new TracingObserveProvider().create(Config.empty(), "test");

        assertThat("Tracing provider tracer",
                   ((TracingObserver) observer).prototype().tracer(),
                   sameInstance(tracer));

        Filter filter = registerFilter((TracingObserver) observer);
        invoke(filter, tracer, semanticConventionsProvider);

        verify(semanticConventionsProvider).create(any(), anyString(), any(), any());
    }

    @Test
    void observeProviderFallsBackToNoOpTracer() {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .discoverServicesFromServiceLoader(false)
                                                                              .build());
        try {
            Services.registry(manager.registry());

            Observer observer = new TracingObserveProvider().create(Config.empty(), "test");

            assertThat("Tracing provider observer type", observer.type(), is("tracing"));
            assertThat("Tracing provider tracer enabled",
                       ((TracingObserver) observer).prototype().tracer().enabled(),
                       is(false));
        } finally {
            manager.shutdown();
        }
    }

    private static Config config(Map<String, String> values) {
        return Config.just(ConfigSources.create(values));
    }

    private static Filter registerFilter(TracingObserver observer) {
        ServerFeature.ServerFeatureContext featureContext = mock(ServerFeature.ServerFeatureContext.class);
        ServerFeature.SocketBuilders socketBuilders = mock(ServerFeature.SocketBuilders.class);
        HttpRouting.Builder routing = mock(HttpRouting.Builder.class);
        when(featureContext.sockets()).thenReturn(Set.of());
        when(featureContext.socket(DEFAULT_SOCKET_NAME)).thenReturn(socketBuilders);
        when(socketBuilders.httpRouting()).thenReturn(routing);

        observer.register(featureContext, List.of(), endpoint -> endpoint);

        ArgumentCaptor<HttpFeature> featureCaptor = ArgumentCaptor.forClass(HttpFeature.class);
        verify(routing).addFeature(featureCaptor.capture());
        featureCaptor.getValue().setup(routing);

        ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
        verify(routing).addFilter(filterCaptor.capture());
        return filterCaptor.getValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invoke(Filter filter,
                               Tracer tracer,
                               TracingSemanticConventionsProvider semanticConventionsProvider) {
        FilterChain chain = mock(FilterChain.class);
        RoutingRequest request = mock(RoutingRequest.class);
        RoutingResponse response = mock(RoutingResponse.class);
        HttpPrologue prologue = mock(HttpPrologue.class);
        TracingSemanticConventions semanticConventions = mock(TracingSemanticConventions.class);
        Span.Builder spanBuilder = mock(Span.Builder.class, RETURNS_SELF);
        Span span = mock(Span.class);
        SpanContext spanContext = mock(SpanContext.class);
        Scope scope = mock(Scope.class);

        when(request.context()).thenReturn(Context.create());
        when(request.prologue()).thenReturn(prologue);
        when(prologue.method()).thenReturn(Method.GET);
        when(prologue.uriPath()).thenReturn(UriPath.create("/test"));
        when(tracer.extract(any())).thenReturn(Optional.empty());
        when(semanticConventionsProvider.create(any(), anyString(), same(request), same(response)))
                .thenReturn(semanticConventions);
        when(semanticConventions.spanName()).thenReturn("test-span");
        when(tracer.spanBuilder("test-span")).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(span);
        when(span.context()).thenReturn(spanContext);
        when(span.activate()).thenReturn(scope);

        filter.filter(chain, request, response);
    }
}
