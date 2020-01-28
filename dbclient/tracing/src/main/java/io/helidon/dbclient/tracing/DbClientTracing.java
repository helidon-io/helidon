/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CompletableFuture;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.dbclient.DbInterceptor;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

/**
 * Tracing interceptor.
 * This interceptor is added through Java Service loader.
 */
public class DbClientTracing implements DbInterceptor {
    static {
        HelidonFeatures.register(HelidonFlavor.SE, "DbClient", "Tracing");
    }

    /**
     * Create a new tracing interceptor based on the configuration.
     *
     * @param config configuration node for this interceptor (currently ignored)
     * @return a new tracing interceptor
     */
    public static DbClientTracing create(Config config) {
        return create();
    }

    /**
     * Create a new interceptor to trace requests.
     * @return a new tracing interceptor
     */
    public static DbClientTracing create() {
        return new DbClientTracing();
    }

    @Override
    public CompletableFuture<DbInterceptorContext> statement(DbInterceptorContext interceptorContext) {
        SpanTracingConfig spanConfig = TracingConfigUtil.spanConfig("dbclient", "statement");

        if (!spanConfig.enabled()) {
            return CompletableFuture.completedFuture(interceptorContext);
        }

        Context context = interceptorContext.context();
        Tracer tracer = context.get(Tracer.class).orElseGet(GlobalTracer::get);

        // now if span context is missing, we build a span without a parent
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(interceptorContext.statementName());

        context.get(SpanContext.class)
                .ifPresent(spanBuilder::asChildOf);

        Span span = spanBuilder.start();

        span.setTag("db.operation", interceptorContext.statementType().toString());
        if (spanConfig.logEnabled("statement", true)) {
            Tags.DB_STATEMENT.set(span, interceptorContext.statement());
        }
        Tags.COMPONENT.set(span, "dbclient");
        Tags.DB_TYPE.set(span, interceptorContext.dbType());

        interceptorContext.statementFuture().thenAccept(nothing -> {
            if (spanConfig.logEnabled("statement-finish", true)) {
                span.log(Map.of("type", "statement"));
            }
        });

        interceptorContext.resultFuture().thenAccept(count -> {
            if (spanConfig.logEnabled("result-finish", true)) {
                span.log(Map.of("type", "result",
                                "count", count));
            }
            span.finish();
        }).exceptionally(throwable -> {
            Tags.ERROR.set(span, Boolean.TRUE);
            span.log(Map.of("event", "error",
                            "error.kind", "Exception",
                            "error.object", throwable,
                            "message", throwable.getMessage()));
            span.finish();
            return null;
        });

        return CompletableFuture.completedFuture(interceptorContext);
    }
}
