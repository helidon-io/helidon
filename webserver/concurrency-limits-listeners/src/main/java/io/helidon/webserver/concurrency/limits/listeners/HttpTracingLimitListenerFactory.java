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

package io.helidon.webserver.concurrency.limits.listeners;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.concurrency.limits.AimdLimit;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.spi.LimitAlgorithmListener;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.service.registry.Service;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.spi.PerRequestLimitAlgorithmListenerFactory;

/**
 * Concurrency limit listener provider for tracing limit algorithm processing for HTTP requests.
 */
@Service.Singleton
class HttpTracingLimitListenerFactory implements PerRequestLimitAlgorithmListenerFactory {

    private final LazyValue<Tracer> tracer = LazyValue.create(Tracer::global);

    private boolean isTracingEnabled;

    @Override
    public void init(Limit limit) {
        isTracingEnabled = limit instanceof FixedLimit fixedLimit ? fixedLimit.prototype().enableTracing()
                : limit instanceof AimdLimit aimdLimit && aimdLimit.prototype().enableTracing();
    }

    @Override
    public Iterable<LimitAlgorithmListener> listeners(HttpPrologue prologue, Headers headers) {
        return isTracingEnabled ? List.of(new Listener(headers)) : List.of();
    }

    private class Listener implements LimitAlgorithmListener {

        private final Headers headers;

        Listener(Headers headers) {
            this.headers = headers;
        }

        @Override
        public void onAccept(String originName, String limitName) {
            // No tracing for immediate acceptance.
        }

        @Override
        public void onReject(String originName, String limitName) {
            // No tracing for immediate rejection.
        }

        @Override
        public void onAccept(String originName, String limitName, long queueingStartTime, long queueingEndTime) {
            recordSpan(originName, limitName, queueingStartTime, true);
        }

        @Override
        public void onReject(String originName, String limitName, long queueingStartTime, long queueingEndTime) {
            recordSpan(originName, limitName, queueingStartTime, false);
        }

        @Override
        public void onDrop() {
        }

        @Override
        public void onIgnore() {
        }

        @Override
        public void onSuccess() {
        }

        private void recordSpan(String originName, String limitName, long queueingStartTime, boolean isOk) {
            var parentSpanContext = tracer.get().extract(headerProvider());

            var spanBuilder = tracer.get().spanBuilder(originName + "-" + limitName + "-limit-span");
            parentSpanContext.ifPresent(sc -> sc.asParent(spanBuilder));
            var span = spanBuilder.start(Instant.ofEpochMilli(queueingStartTime));
            if (isOk) {
                span.end();
            }  else {
                span.status(Span.Status.ERROR);
                span.addEvent("queue limit exceeded");
                span.end();
            }
        }

        private HeaderProvider headerProvider() {
            return new HeaderProvider() {
                @Override
                public Iterable<String> keys() {
                    return headers.stream()
                            .map(h -> h.headerName().lowerCase())
                            .toList();
                }

                @Override
                public Optional<String> get(String key) {
                    return headers.first(HeaderNames.create(key));
                }

                @Override
                public Iterable<String> getAll(String key) {
                    return headers.values(HeaderNames.create(key));
                }

                @Override
                public boolean contains(String key) {
                    return headers.contains(HeaderNames.create(key));
                }
            };
        }
    }
}
