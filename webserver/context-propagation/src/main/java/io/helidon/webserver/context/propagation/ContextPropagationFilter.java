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

package io.helidon.webserver.context.propagation;

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * Filter to add propagation of context values through HTTP headers.
 * This filter will read headers from the request and inject the values into the request
 * {@link io.helidon.common.context.Context}.
 * Note that the values are stored in the context exactly as obtained from request.
 */
public class ContextPropagationFilter implements Handler {
    private final List<PropagationRecord> propagations;

    private ContextPropagationFilter(Builder builder) {
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
     * Create a filter from configuration.
     *
     * @param config configuration to read, must be located on node of server context propagation
     * @return a new context propagation filter
     */
    public static ContextPropagationFilter create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public void accept(ServerRequest req, ServerResponse res) {
        for (PropagationRecord propagation : propagations) {
            propagation.apply(req);
        }
        req.next();
    }

    /**
     * Fluent API builder for {@link io.helidon.webserver.context.propagation.ContextPropagationFilter}.
     */
    @Configured(prefix = "server.context", root = true, description = "Propagation of context data across network")
    public static class Builder implements io.helidon.common.Builder<ContextPropagationFilter> {
        private final List<PropagationRecord> propagations = new LinkedList<>();

        @Override
        public ContextPropagationFilter build() {
            return new ContextPropagationFilter(this);
        }

        /**
         * Update the builder from configuration.
         * Uses the key {@code records} an array of {@link io.helidon.webserver.context.propagation.PropagationRecord}.
         *
         * @param config configuraiton of context propagation
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
         * Add a configuration records. A single record maps header to a context classifier.
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
