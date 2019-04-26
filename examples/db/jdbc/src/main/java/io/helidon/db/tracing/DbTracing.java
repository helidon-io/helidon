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
package io.helidon.db.tracing;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.context.Context;
import io.helidon.db.DbInterceptor;
import io.helidon.db.DbInterceptorContext;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

/**
 * TODO javadoc.
 */
public class DbTracing implements DbInterceptor {
    @Override
    public void statement(DbInterceptorContext interceptorContext) {
        Context context = interceptorContext.context();
        Tracer tracer = context.get(Tracer.class).orElseGet(GlobalTracer::get);

        // now if span context is missing, we build a span without a parent
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(interceptorContext.dbType() + ":" + interceptorContext.statementName());

        context.get(SpanContext.class)
                .ifPresent(spanBuilder::asChildOf);

        Span span = spanBuilder.start();

        interceptorContext.statementFuture().thenAccept(nothing -> span.log(CollectionsHelper.mapOf("type", "statement")));

        interceptorContext.resultFuture().thenAccept(count -> span.log(CollectionsHelper.mapOf("type", "result",
                                                                                               "count", count)).finish())
                .exceptionally(throwable -> {
                    Tags.ERROR.set(span, Boolean.TRUE);
                    span.log(CollectionsHelper.mapOf("event", "error",
                                                     "error.kind", "Exception",
                                                     "error.object", throwable,
                                                     "message", throwable.getMessage()));
                    span.finish();
                    return null;
                });

    }

    public static DbTracing create() {
        return new DbTracing();
    }
}
