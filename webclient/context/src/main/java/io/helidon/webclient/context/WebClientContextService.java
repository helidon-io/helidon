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

package io.helidon.webclient.context;

import java.util.List;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * Client service for context propagation.
 */
@RuntimeType.PrototypedBy(WebClientContextConfig.class)
public class WebClientContextService implements WebClientService, RuntimeType.Api<WebClientContextConfig> {
    private final List<PropagationRecord> propagations;
    private final WebClientContextConfig config;

    private WebClientContextService(WebClientContextConfig config) {
        this.config = config;
        this.propagations = config.records()
                .stream()
                .map(PropagationRecord::create)
                .toList();
    }

    /**
     * Fluent API builder to set up an instance.
     *
     * @return a new builder
     */
    public static WebClientContextConfig.Builder builder() {
        return WebClientContextConfig.builder();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration
     * @return a new service
     */
    public static WebClientContextService create(Config config) {
        return create(WebClientContextConfig.create(config));
    }


    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new service
     */
    public static WebClientContextService create(WebClientContextConfig config) {
        return new WebClientContextService(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new service
     */
    public static WebClientContextService create(Consumer<WebClientContextConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return "context";
    }

    @Override
    public WebClientContextConfig prototype() {
        return config;
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest clientRequest) {
        for (PropagationRecord propagation : propagations) {
            propagation.apply(clientRequest.context(),
                              clientRequest.headers());
        }
        return chain.proceed(clientRequest);
    }
}
