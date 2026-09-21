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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3ResponseSemantics;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.ServerResponseBase;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http.spi.SinkProviderContext;

final class Http3ServerResponse extends ServerResponseBase<Http3ServerResponse> {
    private static final System.Logger LOGGER = System.getLogger(Http3ServerResponse.class.getName());
    private static final Runnable NO_OP = () -> { };

    @SuppressWarnings("rawtypes")
    private final List<SinkProvider> sinkProviders;
    private final ServerResponseHeaders headers;
    private final ServerResponseTrailers trailers;
    private final Http3ConnectionContext ctx;
    private final Http3ServerRequest request;
    private final Http3ServerStream stream;
    private final int responseDispatchWindowSize;
    private final ReentrantLock writeLock = new ReentrantLock();

    private boolean sent;
    private boolean streamingEntity;
    private boolean preparingResponse;
    private boolean headersSent;
    private long bytesWritten;
    private StreamingEntityOutputStream outputStream;
    private String streamResult;

    Http3ServerResponse(Http3ConnectionContext ctx,
                        Http3ServerRequest request,
                        Http3ServerStream stream,
                        int responseDispatchWindowSize,
                        @SuppressWarnings("rawtypes") List<SinkProvider> sinkProviders) {
        super(ctx, request);
        this.ctx = ctx;
        this.request = request;
        this.headers = ServerResponseHeaders.create();
        this.trailers = ServerResponseTrailers.create();
        this.stream = stream;
        if (responseDispatchWindowSize <= 0) {
            throw new IllegalArgumentException("responseDispatchWindowSize must be greater than 0: "
                                                       + responseDispatchWindowSize);
        }
        this.responseDispatchWindowSize = responseDispatchWindowSize;
        this.sinkProviders = List.copyOf(sinkProviders);
    }

    @Override
    public Http3ServerResponse status(Status status) {
        if (outputStream != null) {
            throw new IllegalStateException("Cannot set response status after requesting output stream.");
        }
        return super.status(status);
    }

    @Override
    public Http3ServerResponse header(Header header) {
        if (streamingEntity) {
            throw new IllegalStateException("Cannot set response header after requesting output stream.");
        }
        if (isSent()) {
            throw new IllegalStateException("Cannot set response header after response was already sent.");
        }
        headers.set(header);
        return this;
    }

    @Override
    public void streamFilter(UnaryOperator<OutputStream> filterFunction) {
        Objects.requireNonNull(filterFunction, "filterFunction");
        super.streamFilter(outputStream ->
                                   Objects.requireNonNull(filterFunction.apply(outputStream),
                                                          "output stream filter result"));
    }

    @Override
    public void send(byte[] entityBytes) {
        send(entityBytes, 0, entityBytes.length);
    }

    @Override
    public void send(byte[] entityBytes, int position, int length) {
        Objects.requireNonNull(entityBytes, "entityBytes");
        if (preparingResponse) {
            throw new IllegalStateException("Response preparation already in progress");
        }
        boolean headRequest = request.prologue().method() == Method.HEAD;
        if (headRequest && length > 0) {
            throw new IllegalStateException("Cannot send response entity for a HEAD request");
        }
        if (headRequest && hasStreamFilter()) {
            prepareFilteredHeadResponse();
        }
        if (hasStreamFilter()) {
            // Automatic encoders are skipped for an empty entity, but an explicit encoder still applies.
            boolean allowAutomaticEncoding = length > 0;
            try (OutputStream os = outputStream(allowAutomaticEncoding)) {
                if (!outputStream.noEntityResponse) {
                    os.write(entityBytes, position, length);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return;
        }

        if (sent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("When output stream is used, response is completed by closing "
                                                    + "the output stream.");
        }

        headers.setIfAbsent(HeaderValues.create(HeaderNames.DATE, true, false, DateTime.rfc1123String()));
        boolean noEntityResponse = prepareResponse();

        int actualLength = length;
        int actualPosition = position;
        byte[] actualBytes = entityBytes;
        Status responseStatus = status();
        long configuredHeadLength;
        if (noEntityResponse) {
            normalizeNoEntityHeaders(headers, responseStatus);
            configuredHeadLength = headRequest ? headers.contentLength().orElse(-1) : -1;
            entityBytes(BufferData.EMPTY_BYTES);
            actualBytes = BufferData.EMPTY_BYTES;
            actualPosition = 0;
            actualLength = 0;
        } else {
            configuredHeadLength = headRequest ? headers.contentLength().orElse(-1) : -1;
            actualBytes = entityBytes(entityBytes, position, length);
            if (actualBytes != entityBytes) {
                actualPosition = 0;
                actualLength = actualBytes.length;
            }
            if (configuredHeadLength >= 0) {
                headers.contentLength(configuredHeadLength);
            }
            if (!headRequest || !suppressImplicitContentLength(actualLength)) {
                headers.setIfAbsent(HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                        true,
                                                        false,
                                                        String.valueOf(actualLength)));
            }
        }
        if (noEntityResponse) {
            if (configuredHeadLength >= 0) {
                headers.contentLength(configuredHeadLength);
            }
            normalizeNoEntityHeaders(headers, responseStatus);
        }

        writeLock.lock();
        try {
            bytesWritten = actualLength;
            prepareHeaders();
            OptionalLong contentLength = validateHeaders();
            if (responseSemantics().dataAllowed() && contentLength.isPresent()
                    && contentLength.orElseThrow() != actualLength) {
                throw new IllegalArgumentException("Response data length does not match Content-Length");
            }
            sendFinalResponse(actualBytes, actualPosition, actualLength);
        } finally {
            writeLock.unlock();
        }
        afterSend();
    }

    @Override
    public boolean isSent() {
        return sent;
    }

    @Override
    public OutputStream outputStream() {
        return outputStream(true);
    }

    @Override
    public long bytesWritten() {
        return streamingEntity && outputStream != null ? outputStream.bytesWritten() : bytesWritten;
    }

    @Override
    public ServerResponseHeaders headers() {
        return headers;
    }

    @Override
    public ServerResponseTrailers trailers() {
        if (trailersExpected(headers)) {
            return trailers;
        }
        throw new IllegalStateException(
                "Trailers are supported only when response headers have trailer names definition 'Trailer: <trailer-name>'");
    }

    @Override
    public void streamResult(String result) {
        this.streamResult = Objects.requireNonNull(result, "result");
        if (!headersSent && !responseSemantics().tunnel()) {
            ensureStreamResultTrailer();
        }
    }

    @Override
    public boolean hasEntity() {
        return sent || streamingEntity;
    }

    @Override
    public boolean reset() {
        if (sent || outputStream != null && outputStream.bytesWritten() > 0) {
            return false;
        }
        headers.clear();
        streamingEntity = false;
        headersSent = false;
        outputStream = null;
        bytesWritten = 0;
        resetContentEncoding();
        return true;
    }

    @Override
    public boolean resetStream() {
        if (sent || outputStream != null && outputStream.bytesWritten() > 0) {
            return false;
        }
        streamingEntity = false;
        headersSent = false;
        outputStream = null;
        bytesWritten = 0;
        resetAutomaticContentEncoding();
        return true;
    }

    @Override
    public boolean resetEntity() {
        if (!super.resetEntity()) {
            return false;
        }
        streamResult = null;
        trailers.clear();
        return true;
    }

    @Override
    public void commit() {
        if (outputStream != null) {
            outputStream.commit();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X extends Sink<?>> X sink(GenericType<X> sinkType) {
        for (SinkProvider<?> provider : sinkProviders) {
            if (provider.supports(sinkType, request)) {
                return (X) provider.create(new SinkProviderContext() {
                    @Override
                    public ServerResponse serverResponse() {
                        return Http3ServerResponse.this;
                    }

                    @Override
                    public ServerRequest serverRequest() {
                        return Http3ServerResponse.this.request;
                    }

                    @Override
                    public ConnectionContext connectionContext() {
                        return Http3ServerResponse.this.ctx;
                    }

                    @Override
                    public Optional<OutputStream> entityOutputStream(Runnable responsePreparation) {
                        return Optional.of(Http3ServerResponse.this.outputStream(responsePreparation));
                    }

                    @Override
                    public void flushHeaders() {
                        Http3ServerResponse.this.flushHeaders();
                    }

                    @Override
                    public Runnable closeRunnable() {
                        return Http3ServerResponse.this::commit;
                    }
                });
            }
        }
        throw new HttpException("Unable to find sink provider for request", Status.NOT_ACCEPTABLE_406);
    }

    void flushHeaders() {
        if (outputStream != null) {
            outputStream.flushHeaders();
        }
    }

    void write100Continue() {
        writeLock.lock();
        try {
            if (sent || headersSent) {
                return;
            }
            stream.writeResponseHeaders(Status.CONTINUE_100.code(), WritableHeaders.create(), false);
        } finally {
            writeLock.unlock();
        }
    }

    private static boolean isNoEntityStatus(Status status) {
        int statusCode = status.code();
        return statusCode == Status.NO_CONTENT_204.code()
                || statusCode == Status.RESET_CONTENT_205.code()
                || statusCode == Status.NOT_MODIFIED_304.code();
    }

    private static void normalizeNoEntityHeaders(ServerResponseHeaders headers, Status status) {
        int statusCode = status.code();
        if (statusCode == Status.NO_CONTENT_204.code()) {
            headers.remove(HeaderNames.CONTENT_LENGTH);
        } else if (statusCode == Status.RESET_CONTENT_205.code()) {
            headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
        }
        headers.remove(HeaderNames.TRANSFER_ENCODING);
        headers.remove(HeaderNames.TRAILER);
    }

    private OutputStream outputStream(Runnable responsePreparation) {
        return outputStream(responsePreparation, true);
    }

    private OutputStream outputStream(boolean allowAutomaticEncoding) {
        return outputStream(NO_OP, allowAutomaticEncoding);
    }

    private OutputStream outputStream(Runnable responsePreparation, boolean allowAutomaticEncoding) {
        Objects.requireNonNull(responsePreparation, "responsePreparation");
        if (preparingResponse) {
            throw new IllegalStateException("Response preparation already in progress");
        }
        if (sent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("OutputStream already obtained");
        }

        boolean noEntityResponse = prepareResponse(responsePreparation);
        streamingEntity = true;
        if (!noEntityResponse && !responseSemantics().tunnel()) {
            ensureStreamResultTrailer();
        }

        int configuredWriteBufferSize = ctx.listenerContext().config().writeBufferSize();
        outputStream = new StreamingEntityOutputStream(configuredWriteBufferSize <= 0
                                                               ? 0
                                                               : Math.min(configuredWriteBufferSize,
                                                                          responseDispatchWindowSize),
                                                       noEntityResponse);
        if (noEntityResponse) {
            if (isNoEntityStatus(status())) {
                contentEncode(outputStream, false);
            }
            return new ApplicationOutputStream(outputStream, outputStream);
        }
        OutputStream encodedOutputStream = contentEncode(outputStream, allowAutomaticEncoding);
        OutputStream applicationOutputStream = applyStreamFilters(encodedOutputStream);
        return new ApplicationOutputStream(applicationOutputStream, outputStream);
    }

    private void sendFinalResponse(byte[] actualBytes, int position, int length) {
        boolean trailersExpected = trailersExpected(headers);
        if (responseSemantics().dataAllowed() && length > 0) {
            sendHeaders(false);
            sendData(actualBytes, position, length, !trailersExpected);
            if (trailersExpected) {
                writeTrailers(true);
            }
        } else {
            sendHeaders(!trailersExpected);
            if (trailersExpected) {
                writeTrailers(true);
            }
        }
    }

    private void prepareHeaders() {
        if (!responseSemantics().trailersAllowed()) {
            headers.remove(HeaderNames.TRAILER);
        }
        headers.setIfAbsent(HeaderValues.create(HeaderNames.DATE, DateTime.rfc1123String()));
    }

    private boolean prepareResponse() {
        return prepareResponse(NO_OP);
    }

    private boolean prepareResponse(Runnable responsePreparation) {
        preparingResponse = true;
        try {
            beforeSend();
            responsePreparation.run();
        } finally {
            preparingResponse = false;
        }
        if (status().family() == Status.Family.INFORMATIONAL) {
            LOGGER.log(System.Logger.Level.ERROR,
                       "Attempt to send a final informational response. "
                               + "Server responded with Internal Server Error.");
            status(Status.INTERNAL_SERVER_ERROR_500);
            headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
            headers.remove(HeaderNames.TRANSFER_ENCODING);
            headers.remove(HeaderNames.TRAILER);
            return true;
        }
        return isNoEntityStatus(status());
    }

    private void sendHeaders(boolean last) {
        stream.writeResponseHeaders(status().code(), headers, last);
        headersSent = true;
        sent = true;
    }

    private void sendData(byte[] entityBytes, int position, int length, boolean last) {
        if (length == 0) {
            if (last) {
                sendFin();
            }
            return;
        }
        stream.writeData(entityBytes, position, length, last);
    }

    private void sendFin() {
        stream.writeFin();
    }

    private void writeTrailers(boolean last) {
        finalizeTrailers();
        Http3MessageReader.validateTrailers(trailers);
        stream.writeTrailers(trailers, last);
    }

    private void ensureStreamResultTrailer() {
        if (hasStreamResultTrailer()) {
            return;
        }
        headers.add(STREAM_TRAILERS);
    }

    private boolean hasStreamResultTrailer() {
        if (!headers.contains(HeaderNames.TRAILER)) {
            return false;
        }
        return headers.get(HeaderNames.TRAILER)
                .allValues(true)
                .stream()
                .anyMatch(it -> it.equalsIgnoreCase(STREAM_RESULT_NAME.defaultCase()));
    }

    private void finalizeTrailers() {
        if (hasStreamResultTrailer()) {
            if (streamResult == null) {
                trailers.set(STREAM_RESULT_OK);
            } else {
                trailers.set(STREAM_RESULT_NAME, streamResult);
            }
        }
        Consumer<ServerResponseTrailers> beforeTrailers = beforeTrailers();
        if (beforeTrailers != null) {
            beforeTrailers.accept(trailers);
        }
    }

    private boolean trailersExpected(ServerResponseHeaders headers) {
        return responseSemantics().trailersAllowed() && headers.contains(HeaderNames.TRAILER);
    }

    private Http3ResponseSemantics responseSemantics() {
        return Http3ResponseSemantics.create(request.prologue().method(), status());
    }

    private OptionalLong validateHeaders() {
        return Http3MessageReader.validateResponseHeaders(request.prologue().method(),
                                                          status(),
                                                          headers);
    }

    private static final class ApplicationOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final StreamingEntityOutputStream networkOutputStream;

        private ApplicationOutputStream(OutputStream delegate, StreamingEntityOutputStream networkOutputStream) {
            this.delegate = Objects.requireNonNull(delegate, "output stream filter result");
            this.networkOutputStream = networkOutputStream;
        }

        @Override
        public void write(int value) throws IOException {
            networkOutputStream.checkWriteAllowed(1);
            try {
                delegate.write(value);
            } catch (IOException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            networkOutputStream.checkWriteAllowed(bytes.length);
            try {
                delegate.write(bytes);
            } catch (IOException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            networkOutputStream.checkWriteAllowed(length);
            try {
                delegate.write(bytes, offset, length);
            } catch (IOException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void flush() throws IOException {
            networkOutputStream.checkWriteAllowed(0);
            try {
                delegate.flush();
            } catch (IOException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } catch (IOException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                networkOutputStream.failedWrite(e);
                throw e;
            }
        }
    }

    private final class StreamingEntityOutputStream extends OutputStream {
        private final byte[] writeBuffer;
        private final byte[] singleByte = new byte[1];
        private final boolean headRequest;
        private final boolean noEntityResponse;
        private final Status responseStatus;

        private boolean closed;
        private boolean streamEnded;
        private boolean discardWrites;
        private long bytesWritten;
        private long expectedContentLength = -1;
        private int bufferedBytes;
        private RuntimeException writeFailure;

        private StreamingEntityOutputStream(int writeBufferSize, boolean noEntityResponse) {
            writeBuffer = new byte[writeBufferSize];
            headRequest = request.prologue().method() == Method.HEAD;
            this.noEntityResponse = noEntityResponse;
            responseStatus = status();
        }

        @Override
        public void write(int b) throws IOException {
            if (discardWrites) {
                return;
            }
            writeLock.lock();
            try {
                if (discardWrites) {
                    return;
                }
                singleByte[0] = (byte) b;
                writeBytes(singleByte, 0, 1);
            } catch (UncheckedIOException e) {
                failedWrite(e);
                throw e.getCause();
            } catch (RuntimeException e) {
                failedWrite(e);
                throw e;
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (discardWrites) {
                return;
            }
            Objects.requireNonNull(b, "b");
            Objects.checkFromIndexSize(off, len, b.length);
            writeLock.lock();
            try {
                if (discardWrites) {
                    return;
                }
                writeBytes(b, off, len);
            } catch (UncheckedIOException e) {
                failedWrite(e);
                throw e.getCause();
            } catch (RuntimeException e) {
                failedWrite(e);
                throw e;
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void flush() throws IOException {
            if (discardWrites) {
                return;
            }
            writeLock.lock();
            try {
                if (discardWrites) {
                    return;
                }
                if (closed) {
                    throw new IOException("Stream already closed");
                }
                if (headRequest) {
                    return;
                }
                flushBufferedData(false);
                stream.flushResponseData();
            } catch (UncheckedIOException e) {
                failedWrite(e);
                throw e.getCause();
            } catch (RuntimeException e) {
                failedWrite(e);
                throw e;
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void close() {
            // Closing the entity stream does not commit the response.
        }

        long bytesWritten() {
            return bytesWritten;
        }

        void commit() {
            writeLock.lock();
            try {
                if (closed) {
                    if (writeFailure != null) {
                        throw writeFailure;
                    }
                    return;
                }
                if (discardWrites) {
                    if (writeFailure != null) {
                        throw writeFailure;
                    }
                    return;
                }
                closed = true;
                try {
                    if (noEntityResponse) {
                        normalizeNoEntityHeaders(headers, responseStatus);
                    }
                    boolean trailersExpected = trailersExpected(headers);
                    prepareHeaders();
                    if (!headersSent) {
                        if (!noEntityResponse && responseSemantics().dataAllowed()
                                && !responseSemantics().tunnel()) {
                            headers.setIfAbsent(HeaderValues.CONTENT_LENGTH_ZERO);
                        } else if (!noEntityResponse && headRequest) {
                            headers.setIfAbsent(HeaderValues.create(HeaderNames.CONTENT_LENGTH, bytesWritten));
                        }
                        OptionalLong contentLength = validateHeaders();
                        expectedContentLength = noEntityResponse || !responseSemantics().dataAllowed()
                                ? -1
                                : contentLength.orElse(-1);
                        validateCompleteLength();
                        boolean last = noEntityResponse || !trailersExpected;
                        sendHeaders(last);
                        streamEnded = last;
                        if (trailersExpected) {
                            writeTrailers(true);
                            streamEnded = true;
                        }
                    } else if (!streamEnded) {
                        validateCompleteLength();
                        if (trailersExpected) {
                            flushBufferedData(false);
                            writeTrailers(true);
                            streamEnded = true;
                        } else if (bufferedBytes > 0) {
                            flushBufferedData(true);
                            streamEnded = true;
                        } else {
                            sendFin();
                            streamEnded = true;
                        }
                    }
                    Http3ServerResponse.this.bytesWritten = bytesWritten;
                } catch (RuntimeException e) {
                    failedWrite(e);
                    throw e;
                }
            } finally {
                writeLock.unlock();
            }
            afterSend();
        }

        void flushHeaders() {
            if (discardWrites) {
                return;
            }
            writeLock.lock();
            try {
                if (discardWrites) {
                    return;
                }
                if (closed) {
                    throw new IllegalStateException("Stream already closed");
                }
                if (headersSent) {
                    return;
                }
                try {
                    if (noEntityResponse) {
                        normalizeNoEntityHeaders(headers, responseStatus);
                    }
                    prepareHeaders();
                    OptionalLong contentLength = validateHeaders();
                    expectedContentLength = noEntityResponse || !responseSemantics().dataAllowed()
                            ? -1
                            : contentLength.orElse(-1);
                    boolean trailersExpected = trailersExpected(headers);
                    boolean last = noEntityResponse || headRequest && !trailersExpected;
                    sendHeaders(last);
                    streamEnded = last;
                    stream.flushResponseData();
                } catch (RuntimeException e) {
                    failedWrite(e);
                    throw e;
                }
            } finally {
                writeLock.unlock();
            }
        }

        private void writeBytes(byte[] bytes, int offset, int length) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            if (length == 0) {
                return;
            }
            if (noEntityResponse) {
                throw new IllegalStateException("Attempting to write data on a response with status " + responseStatus);
            }
            long newBytesWritten = Math.addExact(bytesWritten, length);
            if (!headersSent) {
                prepareHeaders();
                OptionalLong contentLength = validateHeaders();
                expectedContentLength = responseSemantics().dataAllowed() ? contentLength.orElse(-1) : -1;
            }
            if (expectedContentLength >= 0 && newBytesWritten > expectedContentLength) {
                throw new IOException("Response data length exceeds Content-Length");
            }
            bytesWritten = newBytesWritten;
            if (headRequest || !responseSemantics().dataAllowed()) {
                return;
            }
            if (!headersSent) {
                sendHeaders(false);
            }
            if (writeBuffer.length == 0) {
                sendData(bytes, offset, length, false);
                return;
            }
            int written = 0;
            while (written < length) {
                if (bufferedBytes == 0 && length - written >= writeBuffer.length) {
                    sendData(bytes, offset + written, length - written, false);
                    return;
                }
                int copied = Math.min(writeBuffer.length - bufferedBytes, length - written);
                System.arraycopy(bytes, offset + written, writeBuffer, bufferedBytes, copied);
                bufferedBytes += copied;
                written += copied;
                if (bufferedBytes == writeBuffer.length) {
                    flushBufferedData(false);
                }
            }
        }

        private void flushBufferedData(boolean last) {
            if (bufferedBytes == 0) {
                return;
            }
            sendData(writeBuffer, 0, bufferedBytes, last);
            bufferedBytes = 0;
        }

        private void validateCompleteLength() {
            if (!noEntityResponse && responseSemantics().dataAllowed() && expectedContentLength >= 0
                    && bytesWritten != expectedContentLength) {
                throw new IllegalStateException("Response data length does not match Content-Length");
            }
        }

        private void checkWriteAllowed(int length) {
            if (discardWrites) {
                if (writeFailure != null) {
                    throw writeFailure;
                }
                return;
            }
            if (length > 0 && headRequest) {
                throw new IllegalStateException("Cannot write response entity for a HEAD request");
            }
            if (length > 0 && noEntityResponse) {
                throw new IllegalStateException("Attempting to write data on a response with status " + responseStatus);
            }
        }

        private void failedWrite(IOException e) {
            if (!discardWrites) {
                discardWrites = true;
                writeFailure = new UncheckedIOException(e);
            }
        }

        private void failedWrite(RuntimeException e) {
            if (!discardWrites) {
                discardWrites = true;
                writeFailure = e;
            }
        }
    }
}
