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

package io.helidon.webserver.http3;

import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.webserver.CloseConnectionException;

final class Http3RequestBodyReader {
    private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;

    private final Http3MessageReader reader;
    private final long maxPayloadSize;
    private final Consumer<Http3ProtocolException> protocolFailureConsumer;
    private final Consumer<QuicStreamException> streamFailureConsumer;

    private long actualLength;
    private boolean endOfEntity;

    Http3RequestBodyReader(Http3MessageReader reader,
                           long maxPayloadSize,
                           Consumer<Http3ProtocolException> protocolFailureConsumer,
                           Consumer<QuicStreamException> streamFailureConsumer) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.maxPayloadSize = maxPayloadSize;
        this.protocolFailureConsumer = Objects.requireNonNull(protocolFailureConsumer, "protocolFailureConsumer");
        this.streamFailureConsumer = Objects.requireNonNull(streamFailureConsumer, "streamFailureConsumer");
    }

    BufferData read(int estimate) {
        if (endOfEntity) {
            return BufferData.empty();
        }

        try {
            BufferData data = reader.readEntityBufferWithTrailers(Math.max(estimate, DEFAULT_CHUNK_SIZE));
            int length = data.available();
            if (length == 0) {
                endOfEntity = true;
                return BufferData.empty();
            }

            actualLength += length;
            if (maxPayloadExceeded(actualLength)) {
                endOfEntity = true;
                reader.close();
                throw new HttpException("Request Entity Too Large", Status.REQUEST_ENTITY_TOO_LARGE_413, true);
            }
            return data;
        } catch (Http3ProtocolException e) {
            protocolFailureConsumer.accept(e);
            throw new CloseConnectionException("HTTP/3 request stream protocol failure", e);
        } catch (QuicStreamException e) {
            streamFailureConsumer.accept(e);
            throw e;
        }
    }

    private boolean maxPayloadExceeded(long entitySize) {
        return maxPayloadSize > -1 && entitySize > maxPayloadSize;
    }
}
