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

package io.helidon.integrations.oci.objectstorage;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Optional;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.Multi;
import io.helidon.integrations.oci.connect.OciResponseParser;

/**
 * Get Object request and response.
 */
public final class GetObject {
    private GetObject() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends ObjectRequest<Request> {
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
        private final Multi<ByteBuffer> publisher;

        private Response(Multi<ByteBuffer> publisher) {
            this.publisher = publisher;
        }

        static Response create(Multi<ByteBuffer> publisher) {
            return new Response(publisher);
        }

        /**
         * Write the response to the provided byte channel.
         *
         * @param channel channel to write to
         * @see java.nio.channels.Channels#newChannel(java.io.OutputStream)
         */
        public void writeTo(WritableByteChannel channel) {
            publisher.to(IoMulti.multiToByteChannel(channel))
                    .await();
        }

    }
}
