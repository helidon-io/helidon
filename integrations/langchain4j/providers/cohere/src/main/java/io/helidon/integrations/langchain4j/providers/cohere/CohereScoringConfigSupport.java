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

package io.helidon.integrations.langchain4j.providers.cohere;

import java.net.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import io.helidon.builder.api.Prototype;

import dev.langchain4j.http.client.HttpClientBuilder;

final class CohereScoringConfigSupport {
    // Generated preBuild processing reapplies programmatic values, while default service discovery invokes a setter
    // only once. Configuration paths identify direct and named values independently of setter order.
    private static final Map<CohereScoringModelConfig.BuilderBase<?, ?>, SetterCounts> SETTER_COUNTS = new WeakHashMap<>();

    private CohereScoringConfigSupport() {
    }

    private static boolean configured(CohereScoringModelConfig.BuilderBase<?, ?> builder, String key) {
        return builder.config()
                .filter(config -> config.get(key).exists() || config.get(key + ".service-registry").exists())
                .isPresent();
    }

    private static int incrementHttpClientBuilder(CohereScoringModelConfig.BuilderBase<?, ?> builder) {
        synchronized (SETTER_COUNTS) {
            var counts = SETTER_COUNTS.getOrDefault(builder, SetterCounts.EMPTY);
            counts = new SetterCounts(counts.httpClientBuilder() + 1, counts.proxy());
            SETTER_COUNTS.put(builder, counts);
            return counts.httpClientBuilder();
        }
    }

    private static int incrementProxy(CohereScoringModelConfig.BuilderBase<?, ?> builder) {
        synchronized (SETTER_COUNTS) {
            var counts = SETTER_COUNTS.getOrDefault(builder, SetterCounts.EMPTY);
            counts = new SetterCounts(counts.httpClientBuilder(), counts.proxy() + 1);
            SETTER_COUNTS.put(builder, counts);
            return counts.proxy();
        }
    }

    private static int httpClientBuilderCount(CohereScoringModelConfig.BuilderBase<?, ?> builder) {
        synchronized (SETTER_COUNTS) {
            return SETTER_COUNTS.getOrDefault(builder, SetterCounts.EMPTY).httpClientBuilder();
        }
    }

    private static void clearHttpClientBuilderCount(CohereScoringModelConfig.BuilderBase<?, ?> builder) {
        synchronized (SETTER_COUNTS) {
            var counts = SETTER_COUNTS.getOrDefault(builder, SetterCounts.EMPTY);
            SETTER_COUNTS.put(builder, new SetterCounts(0, counts.proxy()));
        }
    }

    private static void clearProxyCount(CohereScoringModelConfig.BuilderBase<?, ?> builder) {
        synchronized (SETTER_COUNTS) {
            var counts = SETTER_COUNTS.getOrDefault(builder, SetterCounts.EMPTY);
            SETTER_COUNTS.put(builder, new SetterCounts(counts.httpClientBuilder(), 0));
        }
    }

    private static boolean configuredProxyAppliedAfterHttpClientBuilder(
            CohereScoringModelConfig.BuilderBase<?, ?> builder) {
        return builder.config()
                .filter(config -> config.get("proxy").exists()
                        && !config.get("proxy.service-registry").exists())
                .isPresent()
                && builder.httpClientBuilder()
                .filter(value -> !CohereHttpClientSupport.isProxyAdapter(value))
                .isPresent();
    }

    static final class HttpClientBuilderDecorator
            implements Prototype.OptionDecorator<CohereScoringModelConfig.BuilderBase<?, ?>,
            Optional<HttpClientBuilder>> {

        @Override
        public void decorate(CohereScoringModelConfig.BuilderBase<?, ?> builder,
                             Optional<HttpClientBuilder> httpClientBuilder) {
            if (httpClientBuilder.isEmpty()) {
                clearHttpClientBuilderCount(builder);
                return;
            }
            httpClientBuilder.filter(value -> !CohereHttpClientSupport.isProxyAdapter(value))
                    .ifPresent(ignored -> incrementHttpClientBuilder(builder));
        }
    }

    static final class ProxyDecorator
            implements Prototype.OptionDecorator<CohereScoringModelConfig.BuilderBase<?, ?>, Optional<Proxy>> {

        @Override
        public void decorate(CohereScoringModelConfig.BuilderBase<?, ?> builder, Optional<Proxy> proxy) {
            if (proxy.isEmpty()) {
                builder.httpClientBuilder()
                        .filter(CohereHttpClientSupport::isProxyAdapter)
                        .ifPresent(ignored -> builder.clearHttpClientBuilder());
                clearProxyCount(builder);
                return;
            }

            int proxySetterCount = incrementProxy(builder);
            boolean proxyExplicit = configured(builder, "proxy") || proxySetterCount > 1;
            boolean httpClientBuilderExplicit = configured(builder, "http-client-builder")
                    || httpClientBuilderCount(builder) > 1
                    || configuredProxyAppliedAfterHttpClientBuilder(builder);

            if (httpClientBuilderExplicit) {
                builder.httpClientBuilder()
                        .filter(CohereHttpClientSupport::isProxyAdapter)
                        .ifPresent(ignored -> builder.clearHttpClientBuilder());
            } else if (proxyExplicit
                    && (configured(builder, "proxy") || builder.httpClientBuilder().isPresent())) {
                builder.httpClientBuilder(CohereHttpClientSupport.create(proxy.get()));
            }
        }
    }

    private record SetterCounts(int httpClientBuilder, int proxy) {
        private static final SetterCounts EMPTY = new SetterCounts(0, 0);
    }
}
