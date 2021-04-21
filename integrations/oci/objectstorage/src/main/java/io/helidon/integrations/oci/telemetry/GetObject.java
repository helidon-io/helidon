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
import java.util.Optional;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.integrations.oci.connect.OciResponseParser;

public final class GetObject {
    private GetObject() {
    }

    public static class Request extends ObjectRequest<Request> {
        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
        }
    }

    public static class Response extends OciResponseParser {
        private final Multi<DataChunk> publisher;

        private Response(Multi<DataChunk> publisher) {
            this.publisher = publisher;
        }

        public static Response create(Multi<DataChunk> publisher) {
            return new Response(publisher);
        }

        public Multi<DataChunk> publisher() {
            return publisher;
        }

        public Multi<ByteBuffer> bytePublisher() {
            return publisher.flatMap(it -> Multi.just(it.data()));
        }
    }
}
