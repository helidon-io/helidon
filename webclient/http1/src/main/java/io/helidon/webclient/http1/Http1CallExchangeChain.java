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

package io.helidon.webclient.http1;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

final class Http1CallExchangeChain extends Http1CallChainBase {
    private static final int SLICE_SIZE = 8192;
    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final Http1ClientImpl client;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler uploadHandler;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicInteger pendingIo = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile long lastProgress = System.nanoTime();
    private volatile boolean finished;
    private volatile boolean released;
    private volatile boolean closeAfterUpload;
    private boolean successfulResponse;
    private volatile boolean awaitingContinue;
    private volatile boolean uploadCancelled;
    private boolean uploadSkipped;
    private ExchangeConnection exchangeConnection;
    private Thread uploader;
    private Thread watchdog;

    Http1CallExchangeChain(Http1ClientImpl client,
                           Http1ClientRequestImpl request,
                           CompletableFuture<WebClientServiceRequest> whenSent,
                           CompletableFuture<WebClientServiceResponse> whenComplete,
                           ClientRequest.OutputStreamHandler uploadHandler) {
        super(client, request, whenComplete);
        this.client = client;
        this.whenSent = whenSent;
        this.uploadHandler = uploadHandler;
    }

    @Override
    WebClientServiceResponse doProceed(ClientConnection connection,
                                       WebClientServiceRequest request,
                                       ClientRequestHeaders headers,
                                       DataWriter writer,
                                       DataReader reader,
                                       BufferData prologue) {
        exchangeConnection = new ExchangeConnection(connection, reader, writer);
        if (transportObservation() != null) {
            transportObservation().startDuplex();
        }
        long length = headers.contentLength().orElse(-1);
        boolean chunked = length == -1 || headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        if (chunked) {
            headers.remove(HeaderNames.CONTENT_LENGTH);
            headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        }
        boolean expectContinue = connection.allowExpectContinue()
                && (chunked || length > 0)
                && originalRequest().sendExpectContinue().orElse(clientConfig().sendExpectContinue());
        if (expectContinue) {
            headers.set(HeaderValues.EXPECT_100);
        } else {
            // An explicit Expect header must also use the single response reader below.
            expectContinue = headers.containsToken(HeaderValues.EXPECT_100);
        }
        writeHeaders(connection, headers, prologue, forwardProxy(), protocolConfig().validateRequestHeaders(), sendListener());
        // Ordinary SO_TIMEOUT cannot represent duplex progress: a healthy upload might not have a response yet.
        connection.readTimeout(Duration.ZERO);
        long timeoutNanos = originalRequest().readTimeout().toNanos();
        if (timeoutNanos > 0) {
            watchdog = Thread.ofVirtual().name("helidon-http1-exchange-timeout").start(() -> watch(timeoutNanos));
        }
        write(prologue);

        ResponseHead head = null;
        if (expectContinue) {
            awaitingContinue = true;
            connection.readTimeout(originalRequest().readContinueTimeout());
            boolean statusRead = false;
            try {
                while (head == null) {
                    statusRead = false;
                    Status status = readResponseStatus(exchangeConnection, exchangeConnection.reader(), false);
                    statusRead = true;
                    var responseHeaders = readResponseHeaders(exchangeConnection, exchangeConnection.reader());
                    if (!isPreContinueInterimResponse(status)) {
                        head = new ResponseHead(status, responseHeaders);
                    }
                }
            } catch (UncheckedIOException e) {
                // Searching for a complete status line does not consume its partial bytes.
                if (!statusRead && e.getCause() instanceof SocketTimeoutException) {
                    connection.allowExpectContinue(false);
                } else {
                    throw e;
                }
            } finally {
                awaitingContinue = false;
                connection.readTimeout(Duration.ZERO);
            }
            if (head != null && head.status().code() == Status.CONTINUE_100.code()) {
                head = null;
            }
        }

        if (head == null) {
            uploader = Thread.ofVirtual().name("helidon-http1-exchange-upload").start(() ->
                    Contexts.runInContext(request.context(), () -> {
                        try (var output = new ExchangeOutputStream(length, chunked, request)) {
                            try {
                                uploadHandler.handle(output);
                                if (!output.closed) {
                                    throw new IllegalStateException("Output stream was not closed in handler");
                                }
                            } catch (Throwable e) {
                                // Abandon framing on failure; the transport is closed below.
                                output.closed = true;
                                throw e;
                            }
                        } catch (Throwable e) {
                            if (!uploadCancelled) {
                                abort(e);
                            }
                        }
                    }));
            head = readResponseHead(exchangeConnection, exchangeConnection.reader());
        } else {
            // The declared request entity has not been framed; never put this connection back into the pool.
            uploadSkipped = true;
            whenSent.completeExceptionally(new CancellationException("Server responded before accepting the request entity"));
        }
        successfulResponse = head.status().code() >= 200 && head.status().code() < 300;
        captureProtocolResponse(connection, head.status(), head.headers());
        return createServiceResponse(client, request, exchangeConnection, head.status(), head.headers(),
                                     whenComplete(), transportObservation());
    }

    void awaitUpload() {
        if (uploader != null) {
            try {
                uploader.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                abort(e);
                throw new IllegalStateException("Interrupted waiting for request upload", e);
            }
        }
        checkFailure();
    }

    @Override
    void closeConnectionOnFailure(ClientConnection failedConnection) {
        if (exchangeConnection == null) {
            closeConnection(failedConnection);
        } else {
            closeConnection(exchangeConnection.delegate);
        }
    }

    void checkFailure() {
        Throwable cause = failure.get();
        if (cause instanceof IOException e) {
            throw new UncheckedIOException(e);
        } else if (cause instanceof RuntimeException e) {
            throw e;
        } else if (cause instanceof Error e) {
            throw e;
        } else if (cause != null) {
            throw new IllegalStateException("Request upload failed", cause);
        }
    }

    void abort(Throwable cause) {
        whenSent.completeExceptionally(cause);
        if (failure.compareAndSet(null, cause)) {
            if (transportObservation() != null) {
                transportObservation().fail(cause);
            }
            if (exchangeConnection != null) {
                closeConnection(exchangeConnection.delegate);
            }
        }
        if (uploader != null && uploader != Thread.currentThread()) {
            uploader.interrupt();
        }
    }

    void cancelUpload() {
        uploadCancelled = true;
        whenSent.completeExceptionally(new CancellationException("Upload cancelled after redirect or error response"));
        if (exchangeConnection != null) {
            closeConnection(exchangeConnection.delegate);
        }
        if (uploader != null) {
            uploader.interrupt();
        }
    }

    void finish(boolean success) {
        if (!success) {
            abort(new IllegalStateException("HTTP/1 exchange did not complete"));
        }
        finished = true;
        if (watchdog != null) {
            watchdog.interrupt();
            join(watchdog);
        }
        if (uploader != null) {
            join(uploader);
        }
        if (exchangeConnection != null && !closed.get()) {
            if (success && released && !closeAfterUpload && !uploadSkipped && failure.get() == null) {
                exchangeConnection.delegate.readTimeout(originalRequest().readTimeout());
                exchangeConnection.delegate.releaseResource();
            } else {
                closeConnection(exchangeConnection.delegate);
            }
        }
    }

    private static void join(Thread thread) {
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException _) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeConnection(ClientConnection delegate) {
        if (closed.compareAndSet(false, true)) {
            delegate.closeResource();
        }
    }

    private void watch(long timeoutNanos) {
        while (!finished) {
            LockSupport.parkNanos(Math.min(timeoutNanos, Duration.ofMillis(100).toNanos()));
            if (!awaitingContinue && pendingIo.get() > 0 && System.nanoTime() - lastProgress >= timeoutNanos) {
                abort(new SocketTimeoutException("HTTP/1 exchange made no transport progress within "
                                                         + originalRequest().readTimeout()));
                return;
            }
        }
    }

    private void beginIo() {
        if (pendingIo.get() == 0) {
            lastProgress = System.nanoTime();
        }
        pendingIo.incrementAndGet();
    }

    private void endIo(boolean progress) {
        if (progress) {
            lastProgress = System.nanoTime();
        }
        pendingIo.decrementAndGet();
    }

    private void write(BufferData buffer) {
        beginIo();
        boolean progress = false;
        try {
            sendListener().data(exchangeConnection.helidonSocket(), buffer);
            exchangeConnection.writer().writeNow(buffer);
            progress = true;
        } finally {
            endIo(progress);
        }
    }

    private final class ExchangeOutputStream extends OutputStream {
        private final long contentLength;
        private final boolean chunked;
        private final WebClientServiceRequest request;
        private long written;
        private boolean closed;

        private ExchangeOutputStream(long contentLength, boolean chunked, WebClientServiceRequest request) {
            this.contentLength = chunked ? -1 : contentLength;
            this.chunked = chunked;
            this.request = request;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value});
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed) {
                throw new IOException("Output stream already closed");
            }
            if (contentLength >= 0 && length > contentLength - written) {
                throw new IOException("Request entity exceeds content length " + contentLength);
            }
            for (int remaining = length; remaining > 0;) {
                int count = Math.min(SLICE_SIZE, remaining);
                BufferData buffer;
                if (chunked) {
                    byte[] size = Integer.toHexString(count).getBytes(StandardCharsets.US_ASCII);
                    buffer = BufferData.create(count + size.length + 4);
                    buffer.write(size);
                    buffer.write(CRLF);
                    buffer.write(bytes, offset, count);
                    buffer.write(CRLF);
                } else {
                    buffer = BufferData.create(bytes, offset, count);
                }
                Http1CallExchangeChain.this.write(buffer);
                offset += count;
                remaining -= count;
                written += count;
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                if (contentLength >= 0 && written != contentLength) {
                    throw new IOException("Request entity length " + written + " differs from content length " + contentLength);
                }
                if (chunked) {
                    Http1CallExchangeChain.this.write(BufferData.create(TERMINATING_CHUNK));
                }
                closed = true;
                if (transportObservation() != null) {
                    transportObservation().uploadComplete();
                }
                whenSent.complete(request);
            }
        }
    }

    private final class ExchangeConnection implements ClientConnection {
        private final ClientConnection delegate;
        private final DataReader reader;
        private final DataWriter writer;
        private final HelidonSocket socket;

        private ExchangeConnection(ClientConnection delegate, DataReader source, DataWriter writer) {
            this.delegate = delegate;
            this.writer = writer;
            this.socket = delegate.helidonSocket();
            this.reader = DataReader.create(() -> {
                beginIo();
                boolean progress = false;
                try {
                    source.ensureAvailable();
                    byte[] bytes = source.readBuffer(Math.min(SLICE_SIZE, source.available())).readBytes();
                    progress = bytes.length != 0;
                    return bytes;
                } finally {
                    endIo(progress);
                }
            });
        }

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            return writer;
        }

        @Override
        public String channelId() {
            return delegate.channelId();
        }

        @Override
        public HelidonSocket helidonSocket() {
            return socket;
        }

        @Override
        public void readTimeout(Duration timeout) {
            delegate.readTimeout(timeout);
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public void releaseResource() {
            released = true;
        }

        @Override
        public void closeResource() {
            if (successfulResponse) {
                closeAfterUpload = true;
            } else {
                closeConnection(delegate);
            }
        }
    }
}
