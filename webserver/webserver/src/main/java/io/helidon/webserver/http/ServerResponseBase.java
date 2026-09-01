/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
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
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.UnsupportedTypeException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http.spi.SinkProviderContext;

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
     * Stream result OK.
     */
    protected static final Header STREAM_RESULT_OK = HeaderValues.create(STREAM_RESULT_NAME, "OK");

    /**
     * Stream status trailers.
     */
    protected static final Header STREAM_TRAILERS =
            HeaderValues.createCached(HeaderNames.TRAILER, STREAM_RESULT_NAME.defaultCase());
    private static final HeaderName CONTENT_DIGEST_NAME = HeaderNames.create("Content-Digest");
    private static final HeaderName CONTENT_MD5_NAME = HeaderNames.create("Content-MD5");
    private static final HeaderName DIGEST_NAME = HeaderNames.create("Digest");
    private static final HeaderName REPR_DIGEST_NAME = HeaderNames.create("Repr-Digest");
    private static final Header VARY_ACCEPT_ENCODING =
            HeaderValues.createCached(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME);
    @SuppressWarnings("rawtypes")
    private static final List<SinkProvider> SINK_PROVIDERS =
            HelidonServiceLoader.builder(ServiceLoader.load(SinkProvider.class)).build().asList();
    private final ContentEncodingContext contentEncodingContext;
    private final MediaContext mediaContext;
    private final ServerRequestHeaders requestHeaders;
    private final List<Runnable> beforeSend = new ArrayList<>(5);
    private final List<Runnable> whenSent = new ArrayList<>(5);
    private final int maxInMemory;

    private Status status;
    private boolean nexted;
    private boolean reroute;
    private UriQuery rerouteQuery;
    private String reroutePath;
    private boolean automaticContentEncoding = true;
    private ContentEncoder explicitContentEncoder;
    private ContentEncoder selectedContentEncoder;
    private boolean contentEncodingDataWritten;
    private boolean contentEncodingDiscarded;
    private Consumer<ServerResponseTrailers> beforeTrailers;
    private Runnable responseBeforeSend;
    private UnaryOperator<OutputStream> responseStreamFilter;
    private UnaryOperator<OutputStream> streamFilter;
    private boolean suppressImplicitContentLength;

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
        boolean statusAllowsEntity = statusAllowsEntity(status);
        if (contentEncodingDataWritten && !statusAllowsEntity) {
            throw new IllegalStateException("Cannot set a no-entity response status after response content encoding"
                                                    + " has started");
        }
        if (contentEncodingDiscarded && statusAllowsEntity) {
            throw new IllegalStateException("Cannot set an entity-bearing response status after response content encoding"
                                                    + " was discarded");
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
    public ServerResponse beforeSend(Runnable listener) {
        Objects.requireNonNull(listener);
        Runnable current = responseBeforeSend;
        responseBeforeSend = current == null ? listener : () -> {
            current.run();
            listener.run();
        };
        beforeSend.add(listener);
        return (T) this;
    }

    @Api.Internal
    @Override
    public void entityBeforeSend(Runnable listener) {
        beforeSend.add(Objects.requireNonNull(listener));
    }

    @Override
    public T automaticContentEncoding(boolean enabled) {
        ensureContentEncodingConfigurable();
        this.automaticContentEncoding = enabled;
        return (T) this;
    }

    @Override
    public T contentEncoder(ContentEncoder encoder) {
        Objects.requireNonNull(encoder);
        ensureContentEncodingConfigurable();
        this.explicitContentEncoder = encoder;
        return (T) this;
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
        UriPath uriPath = UriPath.create(reroutePath);
        if (rerouteQuery == null) {
            return prologue.withUriPath(uriPath);
        }
        return HttpPrologue.create(prologue.rawProtocol(),
                                   prologue.protocol(),
                                   prologue.protocolVersion(),
                                   prologue.method(),
                                   uriPath,
                                   rerouteQuery,
                                   prologue.fragment());
    }

    @Override
    public boolean isNexted() {
        return nexted;
    }

    @Override
    public boolean isResponseHandled() {
        return hasEntity() || isNexted() || shouldReroute();
    }

    @Api.Internal
    @Override
    public boolean resetEntity() {
        if (!resetStream()) {
            return false;
        }
        var headers = headers();
        headers.remove(HeaderNames.CONTENT_LENGTH);
        headers.remove(HeaderNames.TRANSFER_ENCODING);
        headers.remove(HeaderNames.TRAILER);
        headers.remove(HeaderNames.CONTENT_RANGE);
        headers.remove(HeaderNames.CONTENT_TYPE);
        headers.remove(HeaderNames.CONTENT_ENCODING);
        headers.remove(HeaderNames.CONTENT_LANGUAGE);
        headers.remove(HeaderNames.CONTENT_LOCATION);
        headers.remove(HeaderNames.CONTENT_DISPOSITION);
        headers.remove(CONTENT_DIGEST_NAME);
        headers.remove(CONTENT_MD5_NAME);
        headers.remove(DIGEST_NAME);
        headers.remove(REPR_DIGEST_NAME);
        headers.remove(HeaderNames.ETAG);
        headers.remove(HeaderNames.LAST_MODIFIED);
        headers.remove(HeaderNames.ACCEPT_RANGES);
        beforeSend.clear();
        if (responseBeforeSend != null) {
            beforeSend.add(responseBeforeSend);
        }
        beforeTrailers = null;
        streamFilter = responseStreamFilter;
        suppressImplicitContentLength = false;
        automaticContentEncoding = true;
        selectedContentEncoder = null;
        return true;
    }

    @Override
    public void streamFilter(UnaryOperator<OutputStream> filterFunction) {
        checkStreamFilter(filterFunction);
        responseStreamFilter = addStreamFilter(responseStreamFilter, filterFunction);
        streamFilter = addStreamFilter(streamFilter, filterFunction);
    }

    @Api.Internal
    @Override
    public void entityStreamFilter(UnaryOperator<OutputStream> filterFunction) {
        checkStreamFilter(filterFunction);
        streamFilter = addStreamFilter(streamFilter, filterFunction);
    }

    @Override
    public ServerResponse beforeTrailers(Consumer<ServerResponseTrailers> beforeTrailers) {
        this.beforeTrailers = beforeTrailers;
        return this;
    }

    /**
     * Gets consumer for server response trailers if registered on this response.
     *
     * @return consumer if registered or {@code null} otherwise
     */
    protected Consumer<ServerResponseTrailers> beforeTrailers() {
        return beforeTrailers;
    }

    /**
     * Find a sink provider for the requested sink type.
     *
     * @param sinkType sink type
     * @param request server request
     * @return matching sink provider
     */
    protected final SinkProvider<?> findSinkProvider(GenericType<? extends Sink<?>> sinkType, ServerRequest request) {
        for (SinkProvider<?> provider : SINK_PROVIDERS) {
            if (provider.supports(sinkType, request)) {
                return provider;
            }
        }
        throw new HttpException("Unable to find sink provider for request", Status.NOT_ACCEPTABLE_406);
    }

    /**
     * Create a sink using the shared provider context and protocol-specific callbacks.
     *
     * @param provider sink provider
     * @param request server request
     * @param connectionContext connection context
     * @param entityOutputStreamProvider protocol entity stream provider
     * @param closeRunnable protocol close callback
     * @param flushHeadersRunnable protocol header flush callback
     * @param <X> sink type
     * @return created sink
     */
    @SuppressWarnings("unchecked")
    protected final <X extends Sink<?>> X createSink(SinkProvider<?> provider,
                                                     ServerRequest request,
                                                     ConnectionContext connectionContext,
                                                     Function<Runnable, Optional<OutputStream>> entityOutputStreamProvider,
                                                     Runnable closeRunnable,
                                                     Runnable flushHeadersRunnable) {
        return (X) provider.create(new SinkProviderContext() {
            @Override
            public ServerResponse serverResponse() {
                return ServerResponseBase.this;
            }

            @Override
            public ServerRequest serverRequest() {
                return request;
            }

            @Override
            public ConnectionContext connectionContext() {
                return connectionContext;
            }

            @Override
            public Optional<OutputStream> entityOutputStream(Runnable responsePreparation) {
                return entityOutputStreamProvider.apply(responsePreparation);
            }

            @Override
            public Runnable closeRunnable() {
                return closeRunnable;
            }

            @Override
            public void flushHeaders() {
                flushHeadersRunnable.run();
            }
        });
    }

    /**
     * Whether this response has any output stream filters.
     *
     * @return whether an output stream filter is configured
     */
    @Api.Internal
    protected final boolean hasStreamFilter() {
        return streamFilter != null;
    }

    /**
     * Discard entity stream filters and suppress an implicit content length for a {@code HEAD} response.
     */
    @Api.Internal
    protected final void prepareFilteredHeadResponse() {
        streamFilter = null;
        suppressImplicitContentLength = true;
    }

    /**
     * Whether the protocol implementation should suppress an implicit content length.
     *
     * @param length response entity length
     * @return whether to suppress the implicit content length
     */
    @Api.Internal
    protected final boolean suppressImplicitContentLength(int length) {
        return suppressImplicitContentLength
                || length == 0 && headers().contains(HeaderNames.CONTENT_ENCODING);
    }

    /**
     * Apply configured output stream filters.
     *
     * @param outputStream output stream to wrap
     * @return filtered output stream
     */
    @Api.Internal
    protected final OutputStream applyStreamFilters(OutputStream outputStream) {
        UnaryOperator<OutputStream> filter = streamFilter;
        return filter == null ? outputStream : filter.apply(outputStream);
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
     * Entity bytes encoded using content encoding. Automatic encoding is skipped for an empty entity. An explicitly
     * configured encoder is applied unless the response status does not allow an entity.
     *
     * @param configuredEntity plain bytes
     * @return encoded bytes or same entity array if encoding is disabled
     */
    protected byte[] entityBytes(byte[] configuredEntity) {
        return entityBytes(configuredEntity, 0, configuredEntity.length);
    }

    /**
     * Entity bytes encoded using content encoding. Automatic encoding is skipped for an empty entity. An explicitly
     * configured encoder is applied unless the response status does not allow an entity.
     *
     * @param configuredEntity plain bytes
     * @param position starting position
     * @param length number of bytes
     * @return encoded bytes or same entity array if encoding is disabled
     */
    protected byte[] entityBytes(byte[] configuredEntity, int position, int length) {
        if (length == 0) {
            if (explicitContentEncoder == null) {
                return configuredEntity;
            }
            if (!statusAllowsEntity(status())) {
                responseContentEncoder(true);
                return configuredEntity;
            }
        }
        ContentEncoder encoder = responseContentEncoder(true);
        if (encoder == ContentEncoder.NO_OP) {
            return configuredEntity;
        }

        // we want to preserve optimization here, let's create a new byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream(length);
        OutputStream os = encoder.apply(baos);
        try {
            os.write(configuredEntity, position, length);
            os.close();
        } catch (IOException e) {
            throw new ServerConnectionException("Failed to write response", e);
        }
        return baos.toByteArray();
    }

    /**
     * Encode content using requested/default content encoder.
     *
     * @param outputStream output stream to write encoded data to
     * @return output stream to write plain data to
     */
    protected OutputStream contentEncode(OutputStream outputStream) {
        return contentEncode(outputStream, true);
    }

    /**
     * Encode content using an explicitly configured encoder, or an automatic encoder when allowed.
     *
     * @param outputStream output stream to write encoded data to
     * @param allowAutomaticEncoding whether automatic encoding may be selected
     * @return output stream to write plain data to
     */
    protected OutputStream contentEncode(OutputStream outputStream, boolean allowAutomaticEncoding) {
        if (!statusAllowsEntity(status())) {
            if (explicitContentEncoder != null) {
                ContentEncoder encoder = responseContentEncoder(false);
                return new DeferredContentEncoderOutputStream(network -> applyContentEncoder(encoder, network),
                                                              outputStream,
                                                              () -> statusAllowsEntity(status()));
            }
            return outputStream;
        }
        return applyContentEncoder(responseContentEncoder(allowAutomaticEncoding), outputStream);
    }

    /**
     * Reset response-layer automatic content encoding to its default behavior.
     */
    protected void resetAutomaticContentEncoding() {
        this.automaticContentEncoding = true;
        this.contentEncodingDataWritten = false;
        this.contentEncodingDiscarded = false;
        if (explicitContentEncoder == null
                && selectedContentEncoder == ContentEncoder.NO_OP
                && !headers().contains(HeaderNames.CONTENT_ENCODING)) {
            selectedContentEncoder = null;
        }
    }

    /**
     * Reset all response-layer content encoding state to its defaults.
     */
    protected void resetContentEncoding() {
        resetAutomaticContentEncoding();
        explicitContentEncoder = null;
        selectedContentEncoder = null;
    }

    /**
     * Execute before send runnables.
     */
    protected void beforeSend() {
        beforeSend.forEach(Runnable::run);
        if (status().code() == Status.NOT_MODIFIED_304.code()
                && contentEncodingContext.contentEncodingEnabled()
                && explicitContentEncoder == null
                && automaticContentEncoding
                && !headers().contains(HeaderNames.CONTENT_ENCODING)
                && !headers().containsToken(VARY_ACCEPT_ENCODING)) {
            headers().add(VARY_ACCEPT_ENCODING);
        }
    }

    /**
     * Execute after send runnables.
     */
    protected void afterSend() {
        for (Runnable runnable : whenSent) {
            runnable.run();
        }
    }

    private static UnaryOperator<OutputStream> addStreamFilter(UnaryOperator<OutputStream> current,
                                                               UnaryOperator<OutputStream> filterFunction) {
        if (current == null) {
            return filterFunction;
        }
        return it -> filterFunction.apply(current.apply(it));
    }

    private ContentEncoder responseContentEncoder(boolean allowAutomaticEncoding) {
        if (selectedContentEncoder != null) {
            return selectedContentEncoder;
        }

        ContentEncoder encoder;
        boolean automaticallySelected = false;
        if (explicitContentEncoder != null) {
            encoder = explicitContentEncoder;
        } else if (!allowAutomaticEncoding
                || !automaticContentEncoding
                || headers().contains(HeaderNames.CONTENT_ENCODING)) {
            encoder = ContentEncoder.NO_OP;
        } else {
            encoder = contentEncodingContext.encoder(requestHeaders);
            automaticallySelected = true;
        }

        encoder.headers(headers());
        if (automaticallySelected) {
            mergeVaryAcceptEncoding();
        }
        selectedContentEncoder = encoder;
        return selectedContentEncoder;
    }

    private static boolean statusAllowsEntity(Status status) {
        int statusCode = status.code();
        return statusCode != Status.NO_CONTENT_204.code()
                && statusCode != Status.RESET_CONTENT_205.code()
                && statusCode != Status.NOT_MODIFIED_304.code();
    }

    private OutputStream applyContentEncoder(ContentEncoder encoder, OutputStream outputStream) {
        OutputStream encodedOutputStream = encoder.apply(outputStream);
        if (encoder == ContentEncoder.NO_OP) {
            return encodedOutputStream;
        }
        return new ContentEncodingOutputStream(encodedOutputStream,
                                               () -> statusAllowsEntity(status()),
                                               () -> contentEncodingDataWritten = true,
                                               () -> contentEncodingDiscarded = true);
    }

    private void ensureContentEncodingConfigurable() {
        if (isSent() || selectedContentEncoder != null) {
            throw new IllegalStateException("Response content encoding already selected");
        }
    }

    private void mergeVaryAcceptEncoding() {
        if (headers().contains(HeaderNames.VARY)) {
            for (String value : headers().get(HeaderNames.VARY).allValues()) {
                String[] values = value.split(",");
                for (String vary : values) {
                    if (HeaderNames.ACCEPT_ENCODING_NAME.equalsIgnoreCase(vary.trim())) {
                        return;
                    }
                }
            }
        }
        headers().add(HeaderValues.create(HeaderNames.VARY, true, false, HeaderNames.ACCEPT_ENCODING_NAME));
    }

    private void checkStreamFilter(UnaryOperator<OutputStream> filterFunction) {
        if (isSent()) {
            throw new IllegalStateException("Response already sent");
        }
        if (hasEntity()) {
            throw new IllegalStateException("OutputStream already obtained");
        }
        Objects.requireNonNull(filterFunction);
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

    private static final class DeferredContentEncoderOutputStream extends OutputStream {
        private final Function<OutputStream, OutputStream> encoder;
        private final OutputStream outputStream;
        private final BooleanSupplier statusAllowsEntity;
        private OutputStream encodedOutputStream;

        private DeferredContentEncoderOutputStream(Function<OutputStream, OutputStream> encoder,
                                                   OutputStream outputStream,
                                                   BooleanSupplier statusAllowsEntity) {
            this.encoder = encoder;
            this.outputStream = outputStream;
            this.statusAllowsEntity = statusAllowsEntity;
        }

        @Override
        public void write(int value) throws IOException {
            outputStream().write(value);
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            outputStream().write(bytes);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            outputStream().write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            outputStream().flush();
        }

        @Override
        public void close() throws IOException {
            outputStream().close();
        }

        private OutputStream outputStream() {
            if (encodedOutputStream == null && statusAllowsEntity.getAsBoolean()) {
                encodedOutputStream = encoder.apply(outputStream);
            }
            return encodedOutputStream == null ? outputStream : encodedOutputStream;
        }
    }

    private static final class ContentEncodingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final BooleanSupplier statusAllowsEntity;
        private final Runnable dataWritten;
        private final Runnable encodingDiscarded;

        private ContentEncodingOutputStream(OutputStream delegate,
                                            BooleanSupplier statusAllowsEntity,
                                            Runnable dataWritten,
                                            Runnable encodingDiscarded) {
            this.delegate = delegate;
            this.statusAllowsEntity = statusAllowsEntity;
            this.dataWritten = dataWritten;
            this.encodingDiscarded = encodingDiscarded;
        }

        @Override
        public void write(int value) throws IOException {
            dataWritten.run();
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            if (bytes.length > 0) {
                dataWritten.run();
            }
            delegate.write(bytes);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length > 0) {
                dataWritten.run();
            }
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            if (!statusAllowsEntity.getAsBoolean()) {
                encodingDiscarded.run();
            }
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            if (!statusAllowsEntity.getAsBoolean()) {
                encodingDiscarded.run();
            }
            delegate.close();
        }
    }
}
