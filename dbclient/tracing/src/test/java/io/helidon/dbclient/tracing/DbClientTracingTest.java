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

package io.helidon.dbclient.tracing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.context.Context;
import io.helidon.dbclient.DbClientContext;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbStatementParameters;
import io.helidon.dbclient.DbStatementType;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

@Testing.Test(perMethod = true)
class DbClientTracingTest {

    @Test
    void defaultTracingServiceCachesRegistryTracerPerInstance() {
        RecordingTracer firstTracer = new RecordingTracer();
        RecordingTracer secondTracer = new RecordingTracer();
        ServiceRegistryManager firstManager = registryManager(firstTracer);
        ServiceRegistryManager secondManager = registryManager(secondTracer);
        DbClientTracing service = DbClientTracing.create();

        try {
            Services.registry(firstManager.registry());
            service.statement(context("first-statement"));

            Services.registry(secondManager.registry());
            service.statement(context("second-statement"));

            assertThat("First registry tracer", firstTracer.spanNames(), contains("first-statement", "second-statement"));
            assertThat("Second registry tracer", secondTracer.spanNames(), empty());
        } finally {
            firstManager.shutdown();
            secondManager.shutdown();
        }
    }

    private static ServiceRegistryManager registryManager(Tracer tracer) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .putContractInstance(Tracer.class, tracer)
                                                     .build());
    }

    private static DbClientServiceContext context(String statementName) {
        DbClientContext clientContext = DbClientContext.builder()
                .dbType("test-db")
                .build();
        DbExecuteContext executeContext = DbExecuteContext.create(statementName, "select 1", clientContext);
        DbClientServiceContext serviceContext = DbClientServiceContext.create(executeContext,
                                                                              DbStatementType.QUERY,
                                                                              CompletableFuture.completedFuture(null),
                                                                              CompletableFuture.completedFuture(1L),
                                                                              DbStatementParameters.UNDEFINED);
        return serviceContext.context(Context.create());
    }

    private static final class RecordingTracer implements Tracer {
        private final Tracer delegate = Tracer.noOp();
        private final List<String> spanNames = new ArrayList<>();

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Span.Builder<?> spanBuilder(String name) {
            spanNames.add(name);
            return delegate.spanBuilder(name);
        }

        @Override
        public Optional<SpanContext> extract(HeaderProvider headersProvider) {
            return delegate.extract(headersProvider);
        }

        @Override
        public void inject(SpanContext spanContext,
                           HeaderProvider inboundHeadersProvider,
                           HeaderConsumer outboundHeadersConsumer) {
            delegate.inject(spanContext, inboundHeadersProvider, outboundHeadersConsumer);
        }

        @Override
        public Tracer register(SpanListener listener) {
            return this;
        }

        private List<String> spanNames() {
            return List.copyOf(spanNames);
        }
    }
}
