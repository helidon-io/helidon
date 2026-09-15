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
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3ReadTimeoutException;
import io.helidon.http.http3.Http3ResponseSemantics;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicSenderStream.SendingStreamState;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webserver.ErrorHandling;

final class Http3ServerStream implements Runnable {
    private static final System.Logger LOGGER = System.getLogger(Http3ServerStream.class.getName());
    private static final int MAX_RESPONSE_DATA_CHUNK_SIZE = 16 * 1024;
    private final Http3ServerConnection serverConnection;
    private final QuicBidiStream stream;
    private final int requestId;
    private final Http3Handler handler;
    private final Http3FrameListener sendFrameListener;
    private final Http3MessageReader reader;
    private final Http3Config config;
    private final Limit requestLimit;
    private final StreamObservation transportObservation;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean exchangeCompleted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean requestBodyClaimed = new AtomicBoolean();
    private final AtomicBoolean requestFailed = new AtomicBoolean();
    private final AtomicReference<QuicStreamException> requestInputFailure = new AtomicReference<>();
    private final ReentrantLock readerCloseLock = new ReentrantLock();
    private final ReentrantLock requestPermitLock = new ReentrantLock();
    private final ReentrantLock responseWriteLock = new ReentrantLock();
    private final ReentrantLock writerLock = new ReentrantLock();

    private volatile boolean requestAdmissionClosed;
    private volatile Http3Protocol.DecodedRequestHead request;
    private volatile Http3ServerResponse response;
    private volatile Http3RequestBodyReader requestBodyReader;
    private volatile int bufferedResponseStatus = -1;
    private volatile LimitAlgorithm.Outcome requestLimitOutcome;
    private boolean readerClosed;
    private Throwable readerCleanupFailure;
    private LimitAlgorithm.Token requestPermit;
    private boolean requestWorkerDone;
    private boolean requestExchangeDone;
    private long pendingResponseDataBytes;
    private volatile CompletableFuture<Void> finalResponseDispatch;
    private volatile QuicStreamWriter writer;
    private volatile StreamOutcome transportCompletionOutcome = StreamOutcome.COMPLETED;

    Http3ServerStream(Http3ServerConnection serverConnection,
                      QuicBidiStream stream,
                      int requestId,
                      Http3Handler handler,
                      Http3Settings localSettings,
                      Duration requestReadTimeout,
                      Http3FrameListener receiveFrameListener,
                      Http3FrameListener sendFrameListener,
                      Http3Config config,
                      Limit requestLimit,
                      StreamObservation transportObservation) {
        this.serverConnection = serverConnection;
        this.stream = stream;
        this.requestId = requestId;
        this.handler = handler;
        this.sendFrameListener = sendFrameListener;
        this.config = config;
        this.requestLimit = Objects.requireNonNull(requestLimit, "requestLimit");
        this.transportObservation = Objects.requireNonNull(transportObservation, "transportObservation");
        this.reader = Http3MessageReader.request(stream,
                                                 serverConnection.qpackContext(),
                                                 serverConnection.connection(),
                                                 config.maxHeadersSize(),
                                                 requestReadTimeout,
                                                 receiveFrameListener);
    }

    @Override
    public void run() {
        if (closed.get()) {
            return;
        }
        try {
            serverConnection.initialized().join();
            sendResponse(handleRequest());
        } catch (Throwable t) {
            handleRequestFailure(t);
        } finally {
            try {
                finishRequestTask();
            } finally {
                requestPermitBoundaryCompleted(true);
            }
        }
    }

    long streamId() {
        return stream.streamId();
    }

    int requestId() {
        return requestId;
    }

    Http3Protocol.DecodedRequestHead request() {
        return Objects.requireNonNull(request, "HTTP/3 request head is not decoded");
    }

    boolean hasRequestBody() {
        return reader.hasEntity();
    }

    OptionalLong contentLength() {
        return reader.contentLength();
    }

    Function<Integer, BufferData> requestBodyReader(long maxPayloadSize) {
        if (!requestBodyClaimed.compareAndSet(false, true)) {
            throw new IllegalStateException("HTTP/3 request body reader already exists for stream " + streamId());
        }
        requestBodyReader = new Http3RequestBodyReader(reader,
                                                       maxPayloadSize,
                                                       failure -> {
                                                           requestFailed.set(true);
                                                           if (failure.scope()
                                                                   == Http3ProtocolException.Scope.CONNECTION) {
                                                               serverConnection.fail(streamId(), failure);
                                                           } else {
                                                               abort(failure.errorCode());
                                                           }
                                                       },
                                                       failure -> {
                                                           requestFailed.set(true);
                                                           requestInputFailure.compareAndSet(null, failure);
                                                       });
        return estimate -> requestBodyReader.read(estimate);
    }

    private Http3QpackContext qpackContext() {
        return serverConnection.qpackContext();
    }

    Http3ConnectionContext context() {
        return serverConnection.context();
    }

    LimitAlgorithm.Outcome requestLimitOutcome() {
        return Objects.requireNonNull(requestLimitOutcome, "HTTP/3 request limit outcome is not available");
    }

    private QuicStreamWriter writer() {
        QuicStreamException inputFailure = requestInputFailure.get();
        if (inputFailure != null) {
            throw inputFailure;
        }
        QuicStreamWriter current = writer;
        if (current != null) {
            return current;
        }
        writerLock.lock();
        try {
            current = writer;
            if (current == null) {
                current = Http3StreamSupport.connectWriter(stream, serverConnection.connection(), sendFrameListener);
                writer = current;
            }
            return current;
        } finally {
            writerLock.unlock();
        }
    }

    Http3ServerResponse response(Http3ServerRequest request) {
        Http3ServerResponse current = response;
        if (current != null) {
            throw new IllegalStateException("HTTP/3 server response already exists for stream " + streamId());
        }
        current = new Http3ServerResponse(context(),
                                          request,
                                          this,
                                          config.responseDispatchWindowSize(),
                                          config.sinkProviders());
        response = current;
        return current;
    }

    long receiveErrorCode() {
        return stream.rcvErrorCode();
    }

    void closeReader() {
        readerCloseLock.lock();
        try {
            if (readerClosed) {
                return;
            }
            readerClosed = true;
            try {
                reader.close();
            } catch (RuntimeException | Error failure) {
                readerCleanupFailure = failure;
                throw failure;
            }
        } finally {
            readerCloseLock.unlock();
        }
    }

    void cancelRequestInput() {
        readerCloseLock.lock();
        try {
            if (readerClosed) {
                return;
            }
            readerClosed = true;
            try {
                if (serverConnection.connection().isOpen()) {
                    stream.requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
                }
            } finally {
                try {
                    reader.close();
                } catch (RuntimeException | Error failure) {
                    readerCleanupFailure = failure;
                    throw failure;
                }
            }
        } finally {
            readerCloseLock.unlock();
        }
    }

    int writeResponseHeaders(int status, Headers headers, boolean last) {
        sendFrameListener.responseHeaders(serverConnection.connection(), streamId(), status, headers);
        byte[] encodedHeaders = Http3Protocol.encodeResponseHeaders(qpackContext(), streamId(), status, headers);
        responseWriteLock.lock();
        try {
            scheduleResponseFrame(BufferData.create(encodedHeaders), last, last);
        } finally {
            responseWriteLock.unlock();
        }
        return encodedHeaders.length;
    }

    void writeData(byte[] bytes, int offset, int length, boolean last) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        responseWriteLock.lock();
        try {
            if (length == 0) {
                if (last) {
                    scheduleResponseFrame(BufferData.empty(), true, true);
                }
                return;
            }
            int written = 0;
            while (written < length) {
                int remainingWindow = config.responseDispatchWindowSize() - Math.toIntExact(pendingResponseDataBytes);
                int chunkSize = Math.min(length - written,
                                         Math.min(MAX_RESPONSE_DATA_CHUNK_SIZE, remainingWindow));
                boolean finalChunk = written + chunkSize == length;
                pendingResponseDataBytes += chunkSize;
                boolean awaitDispatch = pendingResponseDataBytes == config.responseDispatchWindowSize()
                        || (last && finalChunk);
                scheduleResponseFrame(Http3Protocol.encodeDataFrameBuffer(bytes, offset + written, chunkSize),
                                      last && finalChunk,
                                      awaitDispatch);
                written += chunkSize;
            }
        } finally {
            responseWriteLock.unlock();
        }
    }

    void flushResponseData() {
        responseWriteLock.lock();
        try {
            if (pendingResponseDataBytes > 0) {
                scheduleResponseFrame(BufferData.empty(), false, true);
            }
        } finally {
            responseWriteLock.unlock();
        }
    }

    void writeFin() {
        responseWriteLock.lock();
        try {
            scheduleResponseFrame(BufferData.empty(), true, true);
        } finally {
            responseWriteLock.unlock();
        }
    }

    void writeTrailers(Headers trailers, boolean last) {
        sendFrameListener.trailers(serverConnection.connection(), streamId(), trailers);
        responseWriteLock.lock();
        try {
            scheduleResponseFrame(BufferData.create(Http3Protocol.encodeHeadersFrame(qpackContext(),
                                                                                      streamId(),
                                                                                      trailers)),
                                  last,
                                  last);
        } finally {
            responseWriteLock.unlock();
        }
    }

    void reset(long errorCode) {
        stream.reset(errorCode);
    }

    private boolean stopSendingReceived() {
        return stream.stopSendingReceived();
    }

    private long stopSendingErrorCode() {
        return stream.sndErrorCode();
    }

    void activate() {
        if (!active.compareAndSet(false, true)) {
            throw new IllegalStateException("HTTP/3 stream is already active: " + streamId());
        }
    }

    void reject() {
        serverConnection.logRequestRejected(streamId());
        try {
            abort(Http3ErrorCode.REQUEST_REJECTED);
        } finally {
            try {
                completeExchange();
            } finally {
                try {
                    requestTaskCompleted();
                } finally {
                    close(StreamOutcome.REJECTED);
                }
            }
        }
    }

    void requestTaskCompleted() {
        requestPermitBoundaryCompleted(true);
    }

    void connectionClosed(Throwable cause) {
        connectionClosed(cause, StreamOutcome.ERROR);
    }

    void connectionClosed(Throwable cause, StreamOutcome outcome) {
        requestPermitLock.lock();
        try {
            requestAdmissionClosed = true;
            requestFailed.set(true);
        } finally {
            requestPermitLock.unlock();
        }
        serverConnection.logRequestFailure(streamId(), "connection-close", cause);
        try {
            completeExchange();
        } finally {
            close(outcome);
        }
    }

    private Optional<Http3Handler.BufferedResponse> handleRequest() {
        try {
            request = reader.readRequestHead();
            if (!Method.create(request.method()).text().equals(request.method())) {
                return Optional.of(Http3Handler.BufferedResponse.text(Status.NOT_IMPLEMENTED_501.code(),
                                                                      "Not Implemented"));
            }
            return invokeHandler();
        } catch (IllegalArgumentException e) {
            return Optional.of(Http3Handler.BufferedResponse.text(400, "Bad Request"));
        }
    }

    private Optional<Http3Handler.BufferedResponse> invokeHandler() {
        try {
            return handler.handle(this);
        } catch (RequestException e) {
            if (requestReadTimedOut(e)) {
                throw e;
            }
            if (stream.sendingState() != SendingStreamState.READY || responseSent()) {
                throw e;
            }
            ErrorHandling errorHandling = context().listenerContext().config().errorHandling();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)
                    && (e.safeMessage() || errorHandling.logAllMessages())) {
                LOGGER.log(System.Logger.Level.DEBUG, e);
            }
            String message = null;
            if (errorHandling.includeEntity()) {
                message = e.safeMessage() ? e.getMessage() : "Bad request, see server log for more information";
            }
            DirectHandler directHandler = context().listenerContext().directHandlers().handler(e.eventType());
            DirectHandler.TransportResponse response = directHandler.handle(e.request(),
                                                                            e.eventType(),
                                                                            e.status(),
                                                                            e.responseHeaders(),
                                                                            message);
            var responseEntity = response.entity();
            boolean headRequest = Method.HEAD_NAME.equals(request.method());
            boolean preserveHeadContentLength = responseEntity.isEmpty() && headRequest;
            Status responseStatus = response.status();
            boolean finalInformationalResponse = responseStatus.family() == Status.Family.INFORMATIONAL;
            if (finalInformationalResponse) {
                LOGGER.log(System.Logger.Level.ERROR,
                           "Attempt to send a final informational response. "
                                   + "Server responded with Internal Server Error.");
                responseStatus = Status.INTERNAL_SERVER_ERROR_500;
            }
            ServerResponseHeaders headers = response.headers();
            int responseStatusCode = responseStatus.code();
            boolean noContentResponse = responseStatusCode == Status.NO_CONTENT_204.code();
            boolean noEntityResponse = finalInformationalResponse
                    || noContentResponse
                    || responseStatusCode == Status.RESET_CONTENT_205.code()
                    || responseStatusCode == Status.NOT_MODIFIED_304.code();
            byte[] entity;
            if (noEntityResponse) {
                entity = BufferData.EMPTY_BYTES;
                if (noContentResponse) {
                    headers.remove(HeaderNames.CONTENT_LENGTH);
                } else if (finalInformationalResponse || responseStatusCode == Status.RESET_CONTENT_205.code()) {
                    headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
                }
                headers.remove(HeaderNames.TRANSFER_ENCODING);
                headers.remove(HeaderNames.TRAILER);
            } else {
                byte[] responseEntityBytes = responseEntity.orElse(BufferData.EMPTY_BYTES);
                entity = headRequest ? BufferData.EMPTY_BYTES : responseEntityBytes;
                if (!preserveHeadContentLength) {
                    headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                    String.valueOf(responseEntityBytes.length)));
                }
            }
            transportCompletionOutcome = StreamOutcome.REJECTED;
            return Optional.of(Http3Handler.BufferedResponse.create(responseStatus.code(), headers, entity));
        } catch (RuntimeException e) {
            if (e instanceof QuicStreamException || peerCancelled()) {
                throw e;
            }
            requestFailed.set(true);
            if (requestReadTimedOut(e)) {
                throw e;
            }
            Optional<Http3ProtocolException> protocolException = Http3ProtocolException.find(e);
            if (protocolException.isPresent()) {
                throw protocolException.orElseThrow();
            }
            if (stream.sendingState() == SendingStreamState.READY && !responseSent()) {
                return Optional.of(handlerFailureResponse());
            }
            abort(Http3ErrorCode.INTERNAL_ERROR);
            return Optional.empty();
        }
    }

    private void sendResponse(Optional<Http3Handler.BufferedResponse> optionalResponse) {
        if (closed.get() || requestAdmissionClosed || responseSent()) {
            return;
        }
        Http3Handler.BufferedResponse bufferedResponse = optionalResponse.orElse(null);
        if (bufferedResponse == null) {
            if (stream.sendingState() != SendingStreamState.READY || responseSent()) {
                return;
            }
            bufferedResponse = handlerFailureResponse();
        }
        recordResponseStatus(bufferedResponse.status());
        writeBufferedResponse(bufferedResponse);
    }

    private boolean responseSent() {
        Http3ServerResponse current = response;
        return current != null && current.isSent();
    }

    private void handleRequestFailure(Throwable throwable) {
        requestFailed.set(true);
        if (closed.get()) {
            return;
        }
        Throwable cause = Http3RuntimeSupport.unwrap(throwable);
        if (requestReadTimedOut(cause)) {
            serverConnection.logRequestFailure(streamId(), "read-timeout", cause);
            transportCompletionOutcome = StreamOutcome.RESET;
            abort(Http3ErrorCode.REQUEST_CANCELLED);
            return;
        }
        if (peerCancelled()) {
            serverConnection.logRequestCancelled(streamId());
            resetSending(Http3ErrorCode.REQUEST_CANCELLED);
            return;
        }
        if (cause instanceof QuicStreamException streamFailure) {
            serverConnection.logRequestFailure(streamId(), "stream-failed", cause);
            resetSending(streamFailure.errorCode().orElse(-1) == Http3ErrorCode.REQUEST_CANCELLED.code()
                                 ? Http3ErrorCode.REQUEST_CANCELLED
                                 : Http3ErrorCode.INTERNAL_ERROR);
            return;
        }
        Optional<Http3ProtocolException> protocolException = Http3ProtocolException.find(cause);
        if (protocolException.isPresent()) {
            Http3ProtocolException resolved = protocolException.orElseThrow();
            if (resolved.scope() == Http3ProtocolException.Scope.CONNECTION) {
                serverConnection.logRequestFailure(streamId(), "close-connection", cause);
                serverConnection.fail(streamId(), resolved);
            } else {
                serverConnection.logRequestFailure(streamId(), "reset-protocol", cause);
                abort(resolved.errorCode());
            }
            return;
        }
        if (stream.sendingState() == SendingStreamState.READY && !responseSent()) {
            try {
                Http3Handler.BufferedResponse response500 = handlerFailureResponse();
                recordResponseStatus(response500.status());
                writeBufferedResponse(response500);
                serverConnection.logRequestFailure(streamId(), "respond-500", cause);
                return;
            } catch (QuicStreamException _) {
            }
        }
        serverConnection.logRequestFailure(streamId(), "reset", cause);
        abort(Http3ErrorCode.INTERNAL_ERROR);
    }

    private void writeBufferedResponse(Http3Handler.BufferedResponse response) {
        Status status = Status.create(response.status());
        boolean headResponse = Method.HEAD_NAME.equals(request().method());
        Method requestMethod = Method.create(request().method());
        Http3ResponseSemantics semantics = Http3ResponseSemantics.create(requestMethod, status);
        boolean bodyAllowed = semantics.dataAllowed();
        byte[] body = response.body();
        if (!bodyAllowed && !headResponse && body.length > 0) {
            throw new IllegalArgumentException("Response status does not permit message content");
        }
        WritableHeaders<?> headers = WritableHeaders.create(response.headers());
        if ((bodyAllowed || headResponse) && !semantics.tunnel()) {
            headers.setIfAbsent(HeaderValues.create(HeaderNames.CONTENT_LENGTH, body.length));
        }
        OptionalLong contentLength = Http3MessageReader.validateResponseHeaders(requestMethod,
                                                                                 status,
                                                                                 headers);
        if (bodyAllowed && !semantics.tunnel() && contentLength.isPresent()
                && contentLength.orElseThrow() != body.length) {
            throw new IllegalArgumentException("Response data length does not match Content-Length");
        }
        if (bodyAllowed && body.length > 0) {
            writeResponseHeaders(response.status(), headers, false);
            writeData(body, 0, body.length, true);
        } else {
            writeResponseHeaders(response.status(), headers, true);
        }
    }

    private void finishRequest() {
        if (closed.get()) {
            return;
        }
        if (serverConnection.isDraining()
                && serverConnection.connection().isOpen()
                && !stream.sendingState().isTerminal()) {
            stream.futureSendingCompletion().whenComplete((state, throwable) -> {
                if (throwable != null) {
                    requestFailed.set(true);
                }
                try {
                    completeExchange();
                } finally {
                    close(throwable != null
                                  ? StreamOutcome.ERROR
                                  : state != null && state.isReset()
                                          ? StreamOutcome.RESET
                                          : transportCompletionOutcome);
                }
            });
            return;
        }
        completeExchange();
        SendingStreamState state = stream.sendingState();
        if (state.isTerminal()) {
            close(state.isReset() ? StreamOutcome.RESET : transportCompletionOutcome);
        } else {
            stream.futureSendingCompletion().whenComplete((terminalState, throwable) ->
                    close(throwable != null
                                  ? StreamOutcome.ERROR
                                  : terminalState != null && terminalState.isReset()
                                          ? StreamOutcome.RESET
                                          : transportCompletionOutcome));
        }
    }

    private void finishRequestTask() {
        CompletableFuture<Void> dispatch = finalResponseDispatch;
        if (dispatch == null) {
            finishRequestAfterResponseDispatch(null);
            return;
        }
        if (!dispatch.isDone()) {
            dispatch.whenComplete((_, failure) -> executeRequestCompletion(failure));
            return;
        }
        Throwable failure = null;
        try {
            dispatch.join();
        } catch (CancellationException e) {
            failure = e;
        } catch (CompletionException e) {
            failure = e.getCause();
        }
        finishRequestAfterResponseDispatch(failure);
    }

    private void executeRequestCompletion(Throwable dispatchFailure) {
        try {
            serverConnection.executeRequestCompletion(() -> finishRequestAfterResponseDispatch(dispatchFailure));
        } catch (RejectedExecutionException e) {
            serverConnection.logRequestFailure(streamId(), "dispatch-completion-rejected", e);
            finishRequestAfterResponseDispatch(dispatchFailure);
        }
    }

    private void finishRequestAfterResponseDispatch(Throwable dispatchFailure) {
        try {
            if (dispatchFailure != null) {
                handleRequestFailure(dispatchFailure);
            }
        } finally {
            try {
                if (reader.messageComplete()) {
                    closeReader();
                } else {
                    cancelRequestInput();
                }
            } catch (Throwable t) {
                handleRequestFailure(t);
            } finally {
                finishRequest();
            }
        }
    }

    private void completeExchange() {
        if (!exchangeCompleted.compareAndSet(false, true)) {
            return;
        }
        try {
            closeReader();
        } finally {
            requestPermitBoundaryCompleted(false);
        }
    }

    private void requestPermitBoundaryCompleted(boolean workerBoundary) {
        LimitAlgorithm.Token permit;
        requestPermitLock.lock();
        try {
            if (workerBoundary) {
                requestWorkerDone = true;
            } else {
                requestExchangeDone = true;
            }
            if (!requestWorkerDone || !requestExchangeDone) {
                return;
            }
            permit = requestPermit;
            requestPermit = null;
        } finally {
            requestPermitLock.unlock();
        }
        if (permit == null) {
            return;
        }
        Http3ServerResponse currentResponse = response;
        int status = bufferedResponseStatus >= 0
                ? bufferedResponseStatus
                : currentResponse == null ? -1 : currentResponse.status().code();
        try {
            if (requestFailed.get() || status < 0) {
                permit.dropped();
            } else if (status == Status.NOT_FOUND_404.code()) {
                permit.ignore();
            } else if (status >= 100 && status < 400) {
                permit.success();
            } else {
                permit.dropped();
            }
        } catch (Throwable t) {
            serverConnection.logRequestFailure(streamId(), "complete-request-permit", t);
        }
    }

    private void recordResponseStatus(int status) {
        bufferedResponseStatus = status;
    }

    private void close(StreamOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable cleanupFailure = null;
        try {
            closeReader();
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
        }
        readerCloseLock.lock();
        try {
            if (readerCleanupFailure != null) {
                cleanupFailure = readerCleanupFailure;
            }
        } finally {
            readerCloseLock.unlock();
        }
        try {
            transportObservation.close(cleanupFailure == null ? outcome : StreamOutcome.ERROR);
        } catch (RuntimeException | Error failure) {
            serverConnection.logRequestFailure(streamId(), "transport-observation", failure);
        }
        try {
            serverConnection.streamClosed(this, cleanupFailure);
        } catch (RuntimeException | Error failure) {
            if (cleanupFailure == null) {
                cleanupFailure = failure;
            } else if (cleanupFailure != failure) {
                cleanupFailure.addSuppressed(failure);
            }
        }
        if (cleanupFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cleanupFailure instanceof Error error) {
            throw error;
        }
    }

    private boolean peerCancelled() {
        return stopSendingReceived()
                || stopSendingErrorCode() == Http3ErrorCode.REQUEST_CANCELLED.code()
                || stream.rcvErrorCode() == Http3ErrorCode.REQUEST_CANCELLED.code();
    }

    private void abort(Http3ErrorCode errorCode) {
        try {
            readerCloseLock.lock();
            try {
                if (readerClosed) {
                    return;
                }
                readerClosed = true;
                try {
                    stream.requestStopSending(errorCode.code());
                } finally {
                    try {
                        reader.close();
                    } catch (RuntimeException | Error failure) {
                        readerCleanupFailure = failure;
                        throw failure;
                    }
                }
            } finally {
                readerCloseLock.unlock();
            }
        } finally {
            resetSending(errorCode);
        }
    }

    private void resetSending(Http3ErrorCode errorCode) {
        try {
            stream.reset(errorCode.code());
        } catch (QuicStreamException _) {
        }
    }

    private void scheduleResponseFrame(BufferData buffer, boolean last, boolean awaitDispatch) {
        QuicStreamWriter currentWriter = writer();
        if (last) {
            CompletableFuture<Void> dispatch = currentWriter.scheduleForWritingAndGetDispatchCompletion(buffer, true);
            if (dispatch.isDone()) {
                awaitResponseDispatch(dispatch);
            } else {
                finalResponseDispatch = dispatch;
            }
            return;
        }
        if (!awaitDispatch) {
            currentWriter.scheduleForWriting(buffer, false);
            return;
        }
        CompletableFuture<Void> dispatch = currentWriter.scheduleForWritingAndGetDispatchCompletion(buffer, false);
        awaitResponseDispatch(dispatch);
    }

    private void awaitResponseDispatch(CompletableFuture<Void> dispatch) {
        try {
            dispatch.get();
            pendingResponseDataBytes = 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException failure = new InterruptedIOException(
                    "Interrupted while dispatching HTTP/3 response");
            failure.initCause(e);
            try {
                resetSending(Http3ErrorCode.REQUEST_CANCELLED);
            } catch (RuntimeException | Error resetFailure) {
                failure.addSuppressed(resetFailure);
            }
            throw new UncheckedIOException(failure);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof IOException ioException) {
                throw new UncheckedIOException(ioException);
            }
            throw new IllegalStateException("Failed to dispatch HTTP/3 response", cause);
        }
    }

    RequestAdmission admitRequest() {
        requestPermitLock.lock();
        try {
            if (requestAdmissionClosed) {
                return RequestAdmission.CLOSED;
            }
        } finally {
            requestPermitLock.unlock();
        }

        LimitAlgorithm.Outcome outcome = requestLimit.tryAcquireOutcome(false);
        LimitAlgorithm.Token ignoredPermit = null;
        RequestAdmission admission;
        requestPermitLock.lock();
        try {
            if (outcome.disposition() == LimitAlgorithm.Outcome.Disposition.ACCEPTED) {
                LimitAlgorithm.Token permit = ((LimitAlgorithm.Outcome.Accepted) outcome).token();
                if (requestAdmissionClosed) {
                    ignoredPermit = permit;
                    admission = RequestAdmission.CLOSED;
                } else {
                    requestLimitOutcome = outcome;
                    requestPermit = permit;
                    admission = RequestAdmission.ACCEPTED;
                }
            } else {
                admission = requestAdmissionClosed ? RequestAdmission.CLOSED : RequestAdmission.REJECTED;
            }
        } finally {
            requestPermitLock.unlock();
        }
        if (ignoredPermit != null) {
            ignoredPermit.ignore();
        }
        if (admission == RequestAdmission.ACCEPTED) {
            reader.activateReadTimeout();
        }
        return admission;
    }

    private static boolean requestReadTimedOut(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Http3ReadTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Http3Handler.BufferedResponse handlerFailureResponse() {
        return Http3Handler.BufferedResponse.text(500, "HTTP/3 handler failure");
    }

    enum RequestAdmission {
        ACCEPTED,
        REJECTED,
        CLOSED
    }
}
