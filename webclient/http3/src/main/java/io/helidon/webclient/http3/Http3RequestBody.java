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

package io.helidon.webclient.http3;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.EntityWriterPreflight;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;

final class Http3RequestBody {
    private final byte[] bytes;
    private final ClientRequest.OutputStreamHandler streamHandler;
    private final BiConsumer<OutputStream, WritableHeaders<?>> entityWriter;
    private final OptionalLong contentLength;
    private final EntityWriterPreflight.HeaderChanges earlyHeaderChanges;
    private final AtomicBoolean streamHandlerClaimed = new AtomicBoolean();
    private EntityWriterPreflight preflight;
    private EntityWriterPreflight.HeaderChanges terminalHeaderChanges = EntityWriterPreflight.HeaderChanges.empty();

    private Http3RequestBody(byte[] bytes,
                             ClientRequest.OutputStreamHandler streamHandler,
                             BiConsumer<OutputStream, WritableHeaders<?>> entityWriter,
                             OptionalLong contentLength,
                             EntityWriterPreflight.HeaderChanges earlyHeaderChanges) {
        this.bytes = bytes;
        this.streamHandler = streamHandler;
        this.entityWriter = entityWriter;
        this.contentLength = contentLength;
        this.earlyHeaderChanges = earlyHeaderChanges;
    }

    static Http3RequestBody create(byte[] bytes) {
        return create(bytes, EntityWriterPreflight.HeaderChanges.empty());
    }

    static Http3RequestBody create(byte[] bytes, EntityWriterPreflight.HeaderChanges earlyHeaderChanges) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(earlyHeaderChanges, "earlyHeaderChanges");
        return new Http3RequestBody(bytes, null, null, OptionalLong.of(bytes.length), earlyHeaderChanges);
    }

    static Http3RequestBody create(ClientRequest.OutputStreamHandler streamHandler, long contentLength) {
        return create(streamHandler, contentLength, EntityWriterPreflight.HeaderChanges.empty());
    }

    static Http3RequestBody create(ClientRequest.OutputStreamHandler streamHandler,
                                   long contentLength,
                                   EntityWriterPreflight.HeaderChanges earlyHeaderChanges) {
        Objects.requireNonNull(streamHandler, "streamHandler");
        Objects.requireNonNull(earlyHeaderChanges, "earlyHeaderChanges");
        if (contentLength < -1) {
            throw new IllegalArgumentException("Content length must be -1 or greater: " + contentLength);
        }
        return new Http3RequestBody(null,
                                    streamHandler,
                                    null,
                                    contentLength < 0 ? OptionalLong.empty() : OptionalLong.of(contentLength),
                                    earlyHeaderChanges);
    }

    static Http3RequestBody create(BiConsumer<OutputStream, WritableHeaders<?>> entityWriter,
                                   long contentLength,
                                   EntityWriterPreflight.HeaderChanges earlyHeaderChanges) {
        Objects.requireNonNull(entityWriter, "entityWriter");
        Objects.requireNonNull(earlyHeaderChanges, "earlyHeaderChanges");
        if (contentLength < -1) {
            throw new IllegalArgumentException("Content length must be -1 or greater: " + contentLength);
        }
        return new Http3RequestBody(null,
                                    null,
                                    entityWriter,
                                    contentLength < 0 ? OptionalLong.empty() : OptionalLong.of(contentLength),
                                    earlyHeaderChanges);
    }

    OptionalLong contentLength() {
        return contentLength;
    }

    EntityWriterPreflight.HeaderChanges earlyHeaderChanges() {
        return earlyHeaderChanges;
    }

    EntityWriterPreflight.Application prepare(ClientRequestHeaders headers,
                                              Context context,
                                              int bufferCapacity) {
        if (bytes != null && (bytes.length > 0 || headers.contains(HeaderNames.CONTENT_LENGTH))) {
            headers.contentLength(bytes.length);
        }
        if (entityWriter == null) {
            return null;
        }
        if (preflight == null) {
            preflight = EntityWriterPreflight.create(bufferCapacity, context, entityWriter);
            EntityWriterPreflight.Application application = preflight.prepare(headers);
            terminalHeaderChanges = preflight.headerChanges();
            return application;
        }
        return terminalHeaderChanges.apply(headers);
    }

    EntityWriterPreflight.HeaderChanges terminalHeaderChanges() {
        return terminalHeaderChanges;
    }

    boolean canStartAttempt() {
        if (bytes != null) {
            return true;
        }
        if (entityWriter != null) {
            return preflight == null || preflight.canAttach();
        }
        return !streamHandlerClaimed.get();
    }

    boolean isEmpty() {
        return bytes != null && bytes.length == 0;
    }

    void writeTo(OutputStream outputStream) {
        Objects.requireNonNull(outputStream, "outputStream");
        try {
            if (bytes != null) {
                outputStream.write(bytes);
                outputStream.close();
                return;
            }
            if (preflight != null) {
                preflight.writeTo(outputStream);
                return;
            }
            if (!streamHandlerClaimed.compareAndSet(false, true)) {
                throw new IllegalStateException("HTTP/3 request body is one-shot and has already been consumed.");
            }
            streamHandler.handle(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("HTTP/3 request body production failed", e);
        }
    }

    HttpClientResponse submit(HttpClientRequest request) {
        Objects.requireNonNull(request, "request");
        if (bytes != null) {
            return request.submit(bytes);
        }
        return request.outputStream(this::writeTo);
    }

    void cancelIfUnattached() {
        if (preflight != null) {
            preflight.cancelIfUnattached(new CancellationException("HTTP/3 request completed before entity attachment"));
        }
    }
}
