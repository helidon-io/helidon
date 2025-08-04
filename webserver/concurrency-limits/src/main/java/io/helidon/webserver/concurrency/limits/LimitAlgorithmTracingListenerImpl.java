/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.concurrency.limits;

import java.time.Instant;
import java.util.Optional;

import io.helidon.common.concurrency.limits.LimitOutcome;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

@Service.Singleton
final class LimitAlgorithmTracingListenerImpl implements LimitAlgorithmTracingListener {

    private static final Context NO_OP_CONTEXT = new Context();

    private final LimitAlgorithmTracingListenerConfig config;

    LimitAlgorithmTracingListenerImpl(LimitAlgorithmTracingListenerConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "tracing";
    }

    @Override
    public String type() {
        return "tracing";
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public Context onAccept(LimitOutcome.Accepted acceptedLimitOutcome) {
        return acceptedLimitOutcome instanceof LimitOutcome.Deferred deferredOutcome
                ? new DeferredDispositionContext(deferredOutcome)
                : NO_OP_CONTEXT;
    }

    @Override
    public Context onReject(LimitOutcome rejectedLimitOutcome) {
        return NO_OP_CONTEXT;
    }

    @Override
    public void onFinish(LimitAlgorithmTracingListener.Context listenerContext,
                         LimitOutcome.Accepted.ExecutionResult execResult) {
    }

    @Override
    public LimitAlgorithmTracingListenerConfig prototype() {
        return config;
    }

    static class Context implements LimitAlgorithmTracingListener.Context {
    }

    static class DeferredDispositionContext extends Context {

        private final LimitOutcome.Deferred deferredOutcome;
        private final boolean isRecordable;

        DeferredDispositionContext(LimitOutcome.Deferred deferredOutcome) {
            this.deferredOutcome = deferredOutcome;
            isRecordable = deferredOutcome instanceof LimitOutcome.Accepted;
        }

        @Override
        public boolean shouldBePropagated() {
            /*
            This design triggers the span creation from the tracing filter, so we need to have the context
            propagated so the filter can find the context and pass the parent span context.
             */
            return true;
        }

        @Override
        public boolean isRecordable() {
            return isRecordable;
        }

        @Override
        public void createWaitingSpan(Tracer tracer, Optional<SpanContext> parentSpanContext) {
            /*
            The caller should not have invoked this method if the context is not recordable, but check again anyway.
             */
            if (!isRecordable) {
                return;
            }
            recordSpan(deferredOutcome.originName(),
                       deferredOutcome.algorithmType(),
                       deferredOutcome.waitStart(),
                       deferredOutcome.waitEnd(),
                       deferredOutcome instanceof LimitOutcome.Accepted,
                       tracer,
                       parentSpanContext);

        }

        private void recordSpan(String originName, String limitName, long queueingStartTime, long queueingEndTime, boolean isOk,
                                Tracer tracer, Optional<SpanContext> parentSpanContext) {

            var spanBuilder = tracer.spanBuilder(originName + "-" + limitName + "-limit-span");
            parentSpanContext.ifPresent(sc -> sc.asParent(spanBuilder));
            var span = spanBuilder.start(Instant.ofEpochSecond(0, queueingStartTime));
            Instant endInstant = Instant.ofEpochSecond(0, queueingEndTime);
            if (isOk) {
                span.end(endInstant);
            } else {
                span.status(Span.Status.ERROR);
                span.addEvent("queue limit exceeded");
                span.end(endInstant);
            }
        }
    }
}
