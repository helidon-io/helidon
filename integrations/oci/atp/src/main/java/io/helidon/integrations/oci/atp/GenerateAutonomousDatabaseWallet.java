/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.atp;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;
import io.helidon.integrations.oci.connect.OciResponseParser;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * GenerateAutonomousDatabaseWallet request and response.
 */
public final class GenerateAutonomousDatabaseWallet {
    private GenerateAutonomousDatabaseWallet() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends OciRequestBase<Request> {
        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(javax.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends OciResponseParser {
        private final Multi<DataChunk> publisher;

        private Response(Multi<DataChunk> publisher) {
            this.publisher = publisher;
        }

        static Response create(Multi<DataChunk> publisher) {
            return new Response(publisher);
        }

        /**
         * Get a publisher of {@link io.helidon.common.http.DataChunk}, this is useful
         * when the result is sent via WebServer or WebClient that also use it.
         *
         * @return publisher of data chunks
         */
        public Multi<DataChunk> publisher() {
            return publisher;
        }

        /**
         * Get a publisher of byte buffers.
         *
         * @return publisher of byte buffers
         */
        public Multi<ByteBuffer> bytePublisher() {
            return publisher.flatMap(it -> Multi.just(it.data()));
        }
    }
}