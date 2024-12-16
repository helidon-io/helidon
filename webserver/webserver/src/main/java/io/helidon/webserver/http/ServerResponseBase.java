/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.UnsupportedTypeException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;

/**
 * Base class for common server response tasks that can be shared across HTTP versions.
 *
 * @param <T> type of the response extending this class to allow fluent API
 */
@SuppressWarnings("unchecked")
public abstract class ServerResponseBase<T extends ServerResponseBase<T>> implements RoutingResponse {

    /**
     * Stream result trailer name.
     */
    protected static final HeaderName STREAM_RESULT_NAME = HeaderNames.create("stream-result");
    /**
     * Stream status trailers.
     */
    protected static final Header STREAM_TRAILERS =
            HeaderValues.create(HeaderNames.TRAILER, STREAM_RESULT_NAME.defaultCase());
    private final ContentEncodingContext contentEncodingContext;
    private final MediaContext mediaContext;
    private final ServerRequestHeaders requestHeaders;
    private final List<Runnable> whenSent = new ArrayList<>(5);
    private final int maxInMemory;

    private Status status;
    private boolean nexted;
    private boolean reroute;
    private UriQuery rerouteQuery;
    private String reroutePath;

    /**
     * Create server response.
     *
     * @param ctx     context
     * @param request server request
     */
    protected ServerResponseBase(ConnectionContext ctx, ServerRequest request) {
        this.contentEncodingContext = ctx.listenerContext().contentEncodingContext();
        this.mediaContext = ctx.listenerContext().mediaContext();
        this.requestHeaders = request.headers();
        this.maxInMemory = ctx.listenerContext().config().maxInMemoryEntity();
    }

    @Override
    public T status(Status status) {
        if (isSent()) {
            throw new IllegalStateException("Response already sent");
        }
        this.status = status;
        return (T) this;
    }

    @Override
    public Status status() {
        if (status == null) {
            return Status.OK_200;
        }
        return status;
    }

    @Override
    public void send() {
        send(BufferData.EMPTY_BYTES);
    }

    @Override
    public void send(Object entity) {
        if (entity instanceof byte[] bytes) {
            send(bytes);
            return;
        }

        try {
            // now we have to use a media writer, so we may fail
            doSend(entity);
        } catch (UnsupportedTypeException e) {
            throw new HttpException(e.getMessage(), Status.UNSUPPORTED_MEDIA_TYPE_415, e, true);
        }
    }

    @Override
    public T whenSent(Runnable listener) {
        whenSent.add(listener);
        return (T) this;
    }

    @Override
    public T reroute(String newPath) {
        if (nexted) {
            throw new IllegalStateException("Cannot reroute a response that has been nexted");
        }
        this.reroute = true;
        this.reroutePath = newPath;
        return (T) this;
    }

    @Override
    public T reroute(String path, UriQuery query) {
        if (nexted) {
            throw new IllegalStateException("Cannot reroute a response that has been nexted");
        }
        this.reroute = true;
        this.reroutePath = path;
        this.rerouteQuery = query;
        return (T) this;
    }

    @Override
    public T next() {
        if (reroute) {
            throw new IllegalStateException("Cannot next a response that has been rerouted");
        }
        this.nexted = true;
        return (T) this;
    }

    @Override
    public void resetRouting() {
        this.nexted = false;
        this.reroute = false;
        this.reroutePath = null;
        this.rerouteQuery = null;
    }

    @Override
    public boolean shouldReroute() {
        return reroute;
    }

    @Override
    public HttpPrologue reroutePrologue(HttpPrologue prologue) {
        return HttpPrologue.create(prologue.rawProtocol(),
                                   prologue.protocol(),
                                   prologue.protocolVersion(),
                                   prologue.method(),
                                   UriPath.create(reroutePath),
                                   rerouteQuery == null ? prologue.query() : rerouteQuery,
                                   prologue.fragment());
    }

    @Override
    public boolean isNexted() {
        return nexted;
    }

    /**
     * Gets media context for this response.
     *
     * @return the media context
     */
    protected MediaContext mediaContext() {
        return mediaContext;
    }

    /**
     * Entity bytes encoded using content encoding. Does not attempt encoding
     * if entity is empty.
     *
     * @param configuredEntity plain bytes
     * @return encoded bytes or same entity array if encoding is disabled
     */
    protected byte[] entityBytes(byte[] configuredEntity) {
        return entityBytes(configuredEntity, 0, configuredEntity.length);
    }

    /**
     * Entity bytes encoded using content encoding. Does not attempt encoding
     * if entity is empty.
     *
     * @param configuredEntity plain bytes
     * @param position starting position
     * @param length number of bytes
     * @return encoded bytes or same entity array if encoding is disabled
     */
    protected byte[] entityBytes(byte[] configuredEntity, int position, int length) {
        byte[] entity = configuredEntity;
        if (contentEncodingContext.contentEncodingEnabled() && length > 0) {
            ContentEncoder encoder = contentEncodingContext.encoder(requestHeaders);
            // we want to preserve optimization here, let's create a new byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream(length);
            OutputStream os = encoder.apply(baos);
            try {
                os.write(entity, position, length);
                os.close();
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to write response", e);
            }
            entity = baos.toByteArray();
            encoder.headers(headers());
        }
        return entity;
    }

    /**
     * Encode content using requested/default content encoder.
     *
     * @param outputStream output stream to write encoded data to
     * @return output stream to write plain data to
     */
    protected OutputStream contentEncode(OutputStream outputStream) {
        if (contentEncodingContext.contentEncodingEnabled()) {
            ContentEncoder encoder = contentEncodingContext.encoder(requestHeaders);
            encoder.headers(headers());

            return encoder.apply(outputStream);
        }
        return outputStream;
    }

    /**
     * Execute after send runnables.
     */
    protected void afterSend() {
        for (Runnable runnable : whenSent) {
            runnable.run();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void doSend(Object entity) {
        GenericType type;
        if (entity instanceof String) {
            type = GenericType.STRING;
        } else {
            type = GenericType.create(entity);
        }

        EntityWriter writer = mediaContext.writer(type, requestHeaders, headers());
        long configuredContentLength = headers().contentLength().orElse(-1);
        if (writer.supportsInstanceWriter()) {
            InstanceWriter instanceWriter = writer.instanceWriter(type, entity, requestHeaders, headers());
            if (instanceWriter.alwaysInMemory()) {
                send(instanceWriter.instanceBytes());
                return;
            }
            long contentLength = instanceWriter.contentLength().orElse(configuredContentLength);
            if (contentLength != -1 && contentLength < maxInMemory) {
                send(instanceWriter.instanceBytes());
                return;
            }
            instanceWriter.write(outputStream());
            return;
        }


        if (configuredContentLength == -1 || configuredContentLength > maxInMemory) {
            OutputStream outputStream = outputStream();
            writer.write(type, entity, outputStream, requestHeaders, this.headers());
            return;
        }

        // safe to cast to int, as the maxInMemoryEntity configuration option is an int
        ByteArrayOutputStream baos = new ByteArrayOutputStream((int) configuredContentLength);
        writer.write(type, entity, baos, requestHeaders, headers());
        send(baos.toByteArray());
    }
}
