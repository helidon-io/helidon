/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.util.Map;

import io.helidon.common.context.Context;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.common.DbClientServiceBase;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tag;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

/**
 * Tracing interceptor.
 * This interceptor is added through Java Service loader.
 */
public class DbClientTracing extends DbClientServiceBase {
    private static final Tag<? super String> DBCLIENT_TAG = Tag.COMPONENT.create("dbclient");

    private DbClientTracing(Builder builder) {
        super(builder);
    }

    /**
     * Create a new tracing interceptor based on the configuration.
     *
     * @param config configuration node for this interceptor
     * @return a new tracing interceptor
     */
    public static DbClientTracing create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new interceptor to trace requests.
     * @return a new tracing interceptor
     */
    public static DbClientTracing create() {
        return builder().build();
    }

    /**
     * Create a new builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Single<DbClientServiceContext> apply(DbClientServiceContext serviceContext) {
        SpanTracingConfig spanConfig = TracingConfigUtil.spanConfig("dbclient", "statement");

        if (!spanConfig.enabled()) {
            return Single.just(serviceContext);
        }

        Context context = serviceContext.context();
        Tracer tracer = context.get(Tracer.class).orElseGet(Tracer::global);

        // now if span context is missing, we build a span without a parent
        Span.Builder<?> spanBuilder = tracer.spanBuilder(serviceContext.statementName());

        context.get(SpanContext.class)
                .ifPresent(spanBuilder::parent);

        Span span = spanBuilder.start();

        span.tag("db.operation", serviceContext.statementType().toString());
        if (spanConfig.logEnabled("statement", true)) {
            Tag.DB_STATEMENT.create(serviceContext.statement()).apply(span);
        }

        DBCLIENT_TAG.apply(span);
        Tag.DB_TYPE.create(serviceContext.dbType()).apply(span);

        serviceContext.statementFuture().thenAccept(nothing -> {
            if (spanConfig.logEnabled("statement-finish", true)) {
                span.addEvent("log", Map.of("type", "statement"));
            }
        });

        serviceContext.resultFuture().thenAccept(count -> {
            if (spanConfig.logEnabled("result-finish", true)) {
                span.addEvent("log", Map.of("type", "result",
                                "count", count));
            }
            span.end();
        }).exceptionally(throwable -> {
            span.end(throwable);
            return null;
        });

        return Single.just(serviceContext);
    }

    /**
     * Fluent API builder for {@link io.helidon.dbclient.tracing.DbClientTracing}.
     */
    public static class Builder extends DbClientServiceBuilderBase<Builder>
            implements io.helidon.common.Builder<Builder, DbClientTracing> {

        private Builder() {
        }

        @Override
        public DbClientTracing build() {
            return new DbClientTracing(this);
        }
    }
}
