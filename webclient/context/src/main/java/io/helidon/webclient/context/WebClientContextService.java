/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.LinkedList;
import java.util.List;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.context.Context;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * Client service for context propagation.
 */
@RuntimeType.PrototypedBy(WebClientContextConfig.class)
public class WebClientContextService implements WebClientService, RuntimeType.Api<WebClientContextConfig> {
    private final List<ContextRecord> propagations;
    private final WebClientContextConfig config;

    private WebClientContextService(WebClientContextConfig config) {
        this.config = config;
        this.propagations = List.copyOf(builder.records());
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
    public WebClientServiceResponse handle(Chain chain, io.helidon.webclient.api.WebClientServiceRequest clientRequest) {
        for (PropagationRecord propagation : propagations) {
            propagation.apply(request.context(), request.headers());
        }
        return Single.just(request);
        Context ctx = clientRequest.context();
        config.headerMappings()
                .forEach((classifier, headerName) -> ctx.get(classifier, String.class)
                        .ifPresent(it -> clientRequest.headers().set(headerName, it)));
        return chain.proceed(clientRequest);
    }

    /**
     * Fluent API builder for {@link io.helidon.webclient.context.propagation.WebClientContextPropagation}.
     */
    @Configured(prefix = "context", provides = WebClientService.class, description = "Propagation of context data across network")
    public static class Builder implements io.helidon.common.Builder<WebClientContextService> {
        private final List<PropagationRecord> propagations = new LinkedList<>();

        @Override
        public WebClientContextService build() {
            return new WebClientContextService(this);
        }

        /**
         * Update builder from configuration. Reads the {@code records} node.
         *
         * @param config configuration
         * @return updated builder
         */
        public Builder config(Config config) {
            config.get("records")
                    .asList(Config.class)
                    .ifPresent(it -> it.forEach(recordConfig -> addRecord(PropagationRecord.builder().config(recordConfig)
                                                                                  .build())));
            return this;
        }

        /**
         * Add a propagation record to the list of records.
         *
         * @param record record to add
         * @return updated builder
         */
        @ConfiguredOption(key = "records", kind = ConfiguredOption.Kind.LIST, description = "Configuration records")
        public Builder addRecord(PropagationRecord record) {
            propagations.add(record);
            return this;
        }
    }
}
