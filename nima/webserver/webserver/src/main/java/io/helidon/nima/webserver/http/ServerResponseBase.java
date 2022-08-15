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

package io.helidon.nima.webserver.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.http.encoding.ContentEncoder;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.SimpleHandler;

/**
 * Base class for common server response tasks that can be shared across HTTP versions.
 *
 * @param <T> type of the response extending this class to allow fluent API
 */
public abstract class ServerResponseBase<T extends ServerResponseBase<T>> implements RoutingResponse {
    private final ContentEncodingContext contentEncodingContext;
    private final MediaContext mediaContext;
    private final HttpPrologue requestPrologue;
    private final HeadersServerRequest requestHeaders;
    private final List<Runnable> whenSent = new ArrayList<>(5);

    private Http.Status status;
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
        this.contentEncodingContext = ctx.contentEncodingContext();
        this.mediaContext = ctx.mediaContext();
        this.requestPrologue = request.prologue();
        this.requestHeaders = request.headers();
    }

    @Override
    public ServerResponse status(Http.Status status) {
        this.status = status;
        return this;
    }

    @Override
    public Http.Status status() {
        if (status == null) {
            return Http.Status.OK_200;
        }
        return status;
    }

    @Override
    public void send() {
        send(BufferData.EMPTY_BYTES);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void send(Object entity) {
        if (entity instanceof byte[] bytes) {
            send(bytes);
            return;
        }

        GenericType type;
        if (entity instanceof String) {
            type = GenericType.STRING;
        } else {
            type = GenericType.create(entity);
        }

        OutputStream outputStream = outputStream();
        try {
            mediaContext.writer(type, requestHeaders, headers())
                    .write(type, entity, outputStream, requestHeaders, this.headers());
        } catch (IllegalArgumentException e) {
            throw HttpException.builder()
                    .message(e.getMessage())
                    .type(SimpleHandler.EventType.OTHER)
                    .status(Http.Status.UNSUPPORTED_MEDIA_TYPE_415)
                    .request(HttpSimpleRequest.create(requestPrologue, requestHeaders))
                    .build();
        }
    }

    @Override
    public ServerResponse whenSent(Runnable listener) {
        whenSent.add(listener);
        return this;
    }

    @Override
    public ServerResponse reroute(String newPath) {
        if (nexted) {
            throw new IllegalStateException("Cannot reroute a response that has been nexted");
        }
        this.reroute = true;
        this.reroutePath = newPath;
        return this;
    }

    @Override
    public ServerResponse reroute(String path, UriQuery query) {
        if (nexted) {
            throw new IllegalStateException("Cannot reroute a response that has been nexted");
        }
        this.reroute = true;
        this.reroutePath = path;
        this.rerouteQuery = query;
        return this;
    }

    @Override
    public ServerResponse next() {
        if (reroute) {
            throw new IllegalStateException("Cannot next a response that has been rerouted");
        }
        this.nexted = true;
        return this;
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
        return HttpPrologue.create(prologue.protocol(),
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
     * Entity bytes encoded using content encoding.
     *
     * @param configuredEntity plain bytes
     * @return encoded bytes
     */
    protected byte[] entityBytes(byte[] configuredEntity) {
        byte[] entity = configuredEntity;
        if (contentEncodingContext.contentEncodingEnabled()) {
            ContentEncoder encoder = contentEncodingContext.encoder(requestHeaders);
            // we want to preserve optimization here, let's create a new byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream(entity.length);
            OutputStream os = encoder.encode(baos);
            try {
                os.write(entity);
                os.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
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

            return encoder.encode(outputStream);
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
}
