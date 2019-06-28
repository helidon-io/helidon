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
package io.helidon.grpc.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.opentracing.contrib.grpc.OperationNameConstructor;

/**
 * Backward compatibility only.
 * @deprecated use {@link io.helidon.grpc.server.GrpcTracingConfig}
 */
@Deprecated
public class TracingConfiguration extends GrpcTracingConfig {
    private TracingConfiguration(OperationNameConstructor operationNameConstructor,
                                 Set<ServerRequestAttribute> tracedAttributes,
                                 boolean streaming,
                                 boolean verbose) {
        super(operationNameConstructor, tracedAttributes, streaming, verbose);
    }

    /**
     * Builds the configuration of a tracer.
     * @deprecated use {@link GrpcTracingConfig#builder()} instead
     */
    @Deprecated
    public static class Builder {
        /**
         * Creates a Builder with default configuration.
         */
        Builder() {
            operationNameConstructor = OperationNameConstructor.DEFAULT;
            streaming = false;
            verbose = false;
            tracedAttributes = Collections.emptySet();
        }

        /**
         * @param operationNameConstructor for all spans
         * @return this Builder with configured operation name
         */
        public Builder withOperationName(OperationNameConstructor operationNameConstructor) {
            this.operationNameConstructor = operationNameConstructor;
            return this;
        }

        /**
         * @param attributes to set as tags on server spans
         * @return this Builder configured to trace request
         *         attributes
         */
        public Builder withTracedAttributes(ServerRequestAttribute... attributes) {
            tracedAttributes = new HashSet<>(Arrays.asList(attributes));
            return this;
        }

        /**
         * Logs streaming events to server spans.
         *
         * @return this Builder configured to log streaming events
         */
        public Builder withStreaming() {
            streaming = true;
            return this;
        }

        /**
         * Logs all request life-cycle events to server spans.
         *
         * @return this Builder configured to be verbose
         */
        public Builder withVerbosity() {
            verbose = true;
            return this;
        }

        /**
         * @return a GrpcTracingConfig with this Builder's configuration
         */
        public TracingConfiguration build() {
            return new TracingConfiguration(operationNameConstructor, tracedAttributes, streaming, verbose);
        }

        /**
         * A flag indicating whether to log streaming.
         */
        private OperationNameConstructor operationNameConstructor;

        /**
         * A flag indicating verbose logging.
         */
        private boolean streaming;

        /**
         * A flag indicating verbose logging.
         */
        private boolean verbose;

        /**
         * The set of attributes to log in spans.
         */
        private Set<ServerRequestAttribute> tracedAttributes;
    }
}
