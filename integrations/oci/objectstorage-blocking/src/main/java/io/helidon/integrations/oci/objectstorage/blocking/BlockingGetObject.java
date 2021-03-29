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

package io.helidon.integrations.oci.objectstorage.blocking;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.Multi;
import io.helidon.integrations.oci.connect.OciResponseParser;

public final class BlockingGetObject {
    private BlockingGetObject() {
    }

    public static class Response extends OciResponseParser {
        private final Multi<ByteBuffer> publisher;

        private Response(Multi<ByteBuffer> publisher) {
            this.publisher = publisher;
        }

        static Response create(Multi<ByteBuffer> publisher) {
            return new Response(publisher);
        }

        public void writeTo(WritableByteChannel channel) {
            publisher.to(IoMulti.multiToByteChannel(channel))
                    .await();
        }

    }
}
