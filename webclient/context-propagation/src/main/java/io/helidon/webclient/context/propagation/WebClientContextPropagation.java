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

package io.helidon.webclient.context.propagation;

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;

/**
 * Client service for context propagation.
 */
public class WebClientContextPropagation implements WebClientService {
    private final List<PropagationRecord> propagations;

    private WebClientContextPropagation(Builder builder) {
        this.propagations = List.copyOf(builder.propagations);
    }

    /**
     * A new fluent API builder to customize configuration of context propagation.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new instance of client tracing service from configuration.
     *
     * @param config configuration to read from
     * @return a new configured service
     */
    public static WebClientContextPropagation create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
        for (PropagationRecord propagation : propagations) {
            propagation.apply(request.context(), request.headers());
        }
        return Single.just(request);
    }

    /**
     * Fluent API builder for {@link io.helidon.webclient.context.propagation.WebClientContextPropagation}.
     */
    @Configured(prefix = "context", provides = WebClientService.class, description = "Propagation of context data across network")
    public static class Builder implements io.helidon.common.Builder<WebClientContextPropagation> {
        private final List<PropagationRecord> propagations = new LinkedList<>();

        @Override
        public WebClientContextPropagation build() {
            return new WebClientContextPropagation(this);
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
