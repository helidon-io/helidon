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

import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Contexts;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webclient.http3.Http3ExchangeClient.ConnectionSession;
import io.helidon.webclient.http3.Http3ExchangeClient.RequestData;

import static io.helidon.webclient.http3.Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED;
import static io.helidon.webclient.http3.Http3RequestFailureSupport.AttemptDisposition.POSSIBLY_PROCESSED;

final class Http3RequestStream {
    private static final int REQUEST_BUFFER_SIZE = 16 * 1024;
    private static final int REQUEST_DISPATCH_WINDOW_SIZE = 64 * 1024;

    private final ConnectionSession session;
    private final RequestData request;
    private final QuicBidiStream stream;
    private final Http3MessageReader reader;
    private final QuicStreamWriter writer;
    private final Http3FrameListener sendFrameListener;
    private final StreamObservation streamObservation;
    private final Executor responseExecutor;
    private final LongConsumer completionHandler;
    private final LongConsumer responseCancellationHandler;
    private final CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion;
    private final CompletableFuture<Http3StreamedResponse> response = new CompletableFuture<>();
    private final CompletableFuture<Headers> responseTrailers = new CompletableFuture<>();
    private final CompletableFuture<Http3RequestExecution.ContinueDecision> continueDecision =
            new CompletableFuture<>();
    private final AtomicBoolean requestBodyCancelled = new AtomicBoolean();
    private final AtomicBoolean responseBodyCancelled = new AtomicBoolean();
    private final AtomicBoolean requestSent = new AtomicBoolean();
    private final AtomicBoolean executionClaimed = new AtomicBoolean();
    private final AtomicBoolean releaseRequested = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicReference<StreamOutcome> streamOutcome = new AtomicReference<>();
    private final AtomicReference<Throwable> sessionCloseFailure = new AtomicReference<>();
    private final AtomicReference<Http3RequestExecution.Task> producerTask = new AtomicReference<>();
    private final AtomicReference<Http3RequestExecution.Task> responseTask = new AtomicReference<>();
    private final AtomicReference<Http3RequestExecution.Commitment> requestCommitment =
            new AtomicReference<>(Http3RequestExecution.Commitment.NONE);

    Http3RequestStream(ConnectionSession session,
                       RequestData request,
                       QuicBidiStream stream,
                       Http3MessageReader reader,
                       QuicStreamWriter writer,
                       Http3FrameListener sendFrameListener,
                       StreamObservation streamObservation,
                       Executor responseExecutor,
                       LongConsumer completionHandler,
                       LongConsumer responseCancellationHandler) {
        this.session = Objects.requireNonNull(session, "session");
        this.request = Objects.requireNonNull(request, "request");
        this.stream = Objects.requireNonNull(stream, "stream");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.sendFrameListener = Objects.requireNonNull(sendFrameListener, "sendFrameListener");
        this.streamObservation = Objects.requireNonNull(streamObservation, "streamObservation");
        this.responseExecutor = Objects.requireNonNull(responseExecutor, "responseExecutor");
        this.completionHandler = Objects.requireNonNull(completionHandler, "completionHandler");
        this.responseCancellationHandler = Objects.requireNonNull(responseCancellationHandler,
                                                                  "responseCancellationHandler");
        this.sendCompletion = Objects.requireNonNull(stream.futureSendingCompletion(), "sendCompletion");
    }

    private static Throwable classifyRequestFailure(ConnectionSession session,
                                                    QuicBidiStream stream,
                                                    Throwable throwable) {
        return classifyRequestFailure(session,
                                      stream,
                                      throwable,
                                      stream.stopSendingReceived()
                                              ? OptionalLong.of(stream.sndErrorCode())
                                              : OptionalLong.empty());
    }

    private static Throwable classifyRequestFailure(ConnectionSession session,
                                                    QuicBidiStream stream,
                                                    Throwable throwable,
                                                    OptionalLong peerStopCode) {
        boolean receiveReset = stream.receivingState().isReset();
        long receiveErrorCode = stream.rcvErrorCode();
        Throwable cause = Http3RequestFailureSupport.classify(Http3ExchangeClient.unwrap(throwable),
                                                              (receiveReset
                                                                      && receiveErrorCode
                                                                      == Http3ErrorCode.REQUEST_REJECTED.code())
                                                                      || peerStopCode.orElse(-1)
                                                                      == Http3ErrorCode.REQUEST_REJECTED.code(),
                                                              (receiveReset
                                                                      && receiveErrorCode
                                                                      == Http3ErrorCode.VERSION_FALLBACK.code())
                                                                      || peerStopCode.orElse(-1)
                                                                      == Http3ErrorCode.VERSION_FALLBACK.code(),
                                                              session.rejectsStream(stream.streamId()));
        boolean retryable = Http3RequestFailureSupport.isRetryable(cause);
        boolean retireSession = Http3RequestFailureSupport.isRetryableRequestFailure(cause);
        session.logRequestFailure(stream.streamId(), cause, retryable);
        if (retireSession) {
            session.retire("retryable-request-failure");
        }
        return cause;
    }

    void execute(Executor requestExecutor,
                 BiConsumer<Http3StreamedResponse, Throwable> requestCompletion) {
        Objects.requireNonNull(requestExecutor, "requestExecutor");
        Objects.requireNonNull(requestCompletion, "requestCompletion");
        RequestData currentRequest = Objects.requireNonNull(request, "request");
        Http3RequestExecution.Task currentProducerTask = new Http3RequestExecution.Task(
                () -> Contexts.runInContext(currentRequest.context(),
                                            () -> executeRequest().whenComplete(requestCompletion)));
        currentProducerTask.exited().whenComplete((_, _) -> response.whenComplete(requestCompletion));
        if (!producerTask.compareAndSet(null, currentProducerTask)) {
            Http3RequestFailureSupport.RequestAttemptException failure =
                    Http3RequestFailureSupport.attemptFailure(
                            new IllegalStateException("HTTP/3 request producer task is already installed"),
                            NOT_PROCESSED,
                            false);
            currentProducerTask.cancel(false);
            response.completeExceptionally(failure);
            finish(StreamOutcome.ERROR);
            return;
        }
        if (releaseRequested.get()) {
            currentProducerTask.cancel(false);
            return;
        }
        try {
            requestExecutor.execute(currentProducerTask);
        } catch (RuntimeException | Error failure) {
            currentProducerTask.cancel(false);
            Http3RequestFailureSupport.RequestAttemptException attemptFailure =
                    Http3RequestFailureSupport.attemptFailure(failure, NOT_PROCESSED, false);
            response.completeExceptionally(attemptFailure);
            try {
                cancelRequestBody();
            } catch (RuntimeException | Error cancellationFailure) {
                attemptFailure.addSuppressed(cancellationFailure);
            }
            try {
                cancelResponseBody();
            } catch (RuntimeException | Error cancellationFailure) {
                attemptFailure.addSuppressed(cancellationFailure);
            } finally {
                try {
                    finish(StreamOutcome.ERROR);
                } catch (RuntimeException | Error cleanupFailure) {
                    attemptFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    boolean runsOnCurrentThread() {
        Http3RequestExecution.Task currentProducerTask = producerTask.get();
        if (currentProducerTask != null && currentProducerTask.runsOnCurrentThread()) {
            return true;
        }
        Http3RequestExecution.Task currentResponseTask = responseTask.get();
        return currentResponseTask != null && currentResponseTask.runsOnCurrentThread();
    }

    private CompletableFuture<Http3StreamedResponse> executeRequest() {
        if (!executionClaimed.compareAndSet(false, true)) {
            return response;
        }
        ConnectionSession currentSession = Objects.requireNonNull(session, "session");
        RequestData currentRequest = Objects.requireNonNull(request, "request");
        currentSession.logRequestOpen(stream.streamId(),
                                      currentRequest.method().text(),
                                      currentRequest.uri(),
                                      currentRequest.retried());
        RequestOutputStream requestOutput = new RequestOutputStream();
        try {
            Http3RequestExecution.Task task = new Http3RequestExecution.Task(this::readResponse);
            if (!responseTask.compareAndSet(null, task)) {
                throw new IllegalStateException("HTTP/3 response reader task is already installed");
            }
            try {
                responseExecutor.execute(task);
            } catch (RuntimeException | Error failure) {
                responseTask.compareAndSet(task, null);
                task.cancel(false);
                throw failure;
            }
            stream.whenStopSendingReceived().whenComplete((errorCode, stopFailure) -> {
                if (stopFailure != null
                        || errorCode == Http3ErrorCode.NO_ERROR.code()
                        || errorCode == Http3ErrorCode.REQUEST_CANCELLED.code()) {
                    return;
                }
                Throwable cause = classifyRequestFailure(
                        currentSession,
                        stream,
                        new IllegalStateException(
                                "HTTP/3 peer requested that request-body sending stop (error code "
                                        + errorCode + ")."),
                        OptionalLong.of(errorCode));
                Http3RequestFailureSupport.RequestAttemptException attemptFailure =
                        Http3RequestFailureSupport.attemptFailure(
                                cause,
                                attemptDisposition(cause),
                                !Http3RequestFailureSupport.isRetryableRequestFailure(cause)
                                        && currentSession.connection().termination().isEmpty()
                                        && !currentSession.isRetired());
                if (!response.completeExceptionally(attemptFailure)) {
                    return;
                }
                continueDecision.completeExceptionally(cause);
                try {
                    cancelResponseBody();
                } catch (RuntimeException | Error cleanupFailure) {
                    cause.addSuppressed(cleanupFailure);
                }
                try {
                    finish(failureOutcome(cause));
                } catch (RuntimeException | Error cleanupFailure) {
                    cause.addSuppressed(cleanupFailure);
                }
            });
            if (releaseRequested.get()) {
                return response;
            }
            currentRequest.requestBody().writeTo(new BufferedOutputStream(requestOutput, REQUEST_BUFFER_SIZE) {
                @Override
                public void close() throws IOException {
                    if (requestOutput.dispatchFailure == null) {
                        super.close();
                    }
                }
            });
            RuntimeException dispatchFailure = requestOutput.dispatchFailure;
            if (dispatchFailure != null) {
                throw dispatchFailure;
            }
            if (requestOutput.interrupted()) {
                response.thenRun(this::signalRequestSent);
                return response;
            }
            if (!requestOutput.closed()) {
                throw new IllegalStateException("HTTP/3 request output stream was not closed by its producer.");
            }
        } catch (Http3RequestExecution.BodyInterruptedException _) {
            response.thenRun(this::signalRequestSent);
            return response;
        } catch (RuntimeException | Error e) {
            Throwable cause = classifyRequestFailure(currentSession, stream, e);
            Http3RequestFailureSupport.RequestAttemptException attemptFailure =
                    Http3RequestFailureSupport.attemptFailure(
                            cause,
                            attemptDisposition(cause),
                            (Http3RequestFailureSupport.isVersionFallback(cause)
                                    || (requestOutput.dispatchFailure == null
                                    && !Http3RequestFailureSupport.isRetryableRequestFailure(cause)))
                                    && currentSession.connection().termination().isEmpty()
                                    && !currentSession.isRetired());
            boolean localFailureSelected = response.completeExceptionally(attemptFailure);
            boolean canonicalFailureSelected = response.isCompletedExceptionally();
            if (localFailureSelected || !canonicalFailureSelected) {
                cancelRequestBody();
                try {
                    cancelResponseBody();
                } catch (RuntimeException cancellationFailure) {
                    cause.addSuppressed(cancellationFailure);
                }
                finish(failureOutcome(cause));
            }
            return canonicalFailureSelected ? response : CompletableFuture.failedFuture(attemptFailure);
        }
        return response;
    }

    private void readResponse() {
        ConnectionSession currentSession = Objects.requireNonNull(session, "session");
        try {
            Http3MessageReader.ResponseHead responseHead = reader.readResponseHead(informational -> {
                if (informational.status() == Status.CONTINUE_100) {
                    continueDecision.complete(Http3RequestExecution.ContinueDecision.SEND_BODY);
                }
            });
            Instant receivedAt = Instant.now();
            reader.activateReadTimeout();
            continueDecision.complete(Http3RequestExecution.ContinueDecision.RESPONSE_READY);
            boolean hasEntity = reader.hasEntity();
            currentSession.logResponse(stream.streamId(), responseHead.status().code(), hasEntity);
            if (!hasEntity) {
                reader.readEntityDataWithTrailers(1);
                responseTrailers.complete(reader.trailers());
                response.complete(new Http3StreamedResponse(
                        responseHead,
                        currentSession.resolvedTarget(),
                        receivedAt,
                        responseTrailers,
                        null,
                        this,
                        false));
                return;
            }
            Http3StreamedResponse streamedResponse = new Http3StreamedResponse(
                    responseHead,
                    currentSession.resolvedTarget(),
                    receivedAt,
                    responseTrailers,
                    null,
                    this,
                    true);
            InputStream responseInput = reader.inputStreamWithTrailers(responseTrailers::complete,
                                                                        streamedResponse::entityFullyRead,
                                                                        failure -> responseProtocolFailure(
                                                                                failure,
                                                                                responseTrailers));
            streamedResponse.inputStream(new ReadTimeoutInputStream(responseInput, streamedResponse));
            response.complete(streamedResponse);
        } catch (Throwable t) {
            Optional<Http3ProtocolException> protocolFailure = Http3ProtocolException.find(t);
            SocketTimeoutException readTimeout = null;
            Throwable current = t;
            while (current != null) {
                if (current instanceof SocketTimeoutException timeout) {
                    readTimeout = timeout;
                    break;
                }
                Throwable next = current.getCause();
                if (next == current) {
                    break;
                }
                current = next;
            }
            boolean timedOut = readTimeout != null;
            Throwable cause;
            if (timedOut) {
                cause = new UncheckedIOException(readTimeout);
                continueDecision.completeExceptionally(cause);
                responseReadTimedOut(cause, responseTrailers);
            } else {
                protocolFailure.ifPresent(it -> responseProtocolFailure(it, responseTrailers));
                cause = classifyRequestFailure(currentSession, stream, t);
                continueDecision.completeExceptionally(cause);
            }
            if (!timedOut && protocolFailure.isEmpty()) {
                cancelRequestBody();
                finish(failureOutcome(cause));
            }
            response.completeExceptionally(
                    Http3RequestFailureSupport.attemptFailure(
                            cause,
                            attemptDisposition(cause),
                            timedOut
                                    || protocolFailure.filter(it -> it.scope() == Http3ProtocolException.Scope.STREAM)
                                    .isPresent()
                                    || (Http3RequestFailureSupport.isVersionFallback(cause)
                                    && currentSession.connection().termination().isEmpty()
                                    && !currentSession.isRetired())));
        }
    }

    void responseReadTimedOut(Throwable failure, CompletableFuture<Headers> trailers) {
        trailers.completeExceptionally(failure);
        try {
            cancelRequestBody();
        } catch (RuntimeException cleanupFailure) {
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        try {
            cancelResponseBody();
        } catch (RuntimeException cleanupFailure) {
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        try {
            finish(StreamOutcome.ERROR);
        } catch (RuntimeException cleanupFailure) {
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private Http3RequestFailureSupport.AttemptDisposition attemptDisposition(Throwable cause) {
        if (Http3RequestFailureSupport.isVersionFallback(cause)) {
            return Http3RequestFailureSupport.AttemptDisposition.VERSION_FALLBACK;
        }
        if (Http3RequestFailureSupport.isRetryableRequestFailure(cause)) {
            return NOT_PROCESSED;
        }
        return requestCommitment.get() == Http3RequestExecution.Commitment.NONE
                ? NOT_PROCESSED
                : POSSIBLY_PROCESSED;
    }

    private void dispatch(BufferData buffer, boolean last, boolean awaitDispatch) {
        if (releaseRequested.get()) {
            throw new IllegalStateException("HTTP/3 request stream is closed");
        }
        try {
            if (awaitDispatch) {
                writer.scheduleForWritingAndGetDispatchCompletion(buffer, last).join();
            } else {
                writer.scheduleForWriting(buffer, last);
            }
        } catch (CompletionException e) {
            Throwable cause = Http3ExchangeClient.unwrap(e);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof IOException ioException) {
                throw new UncheckedIOException("Failed to dispatch HTTP/3 request stream data", ioException);
            }
            throw new IllegalStateException("Failed to dispatch HTTP/3 request stream data", cause);
        }
    }

    private void signalRequestSent() {
        if (requestSent.compareAndSet(false, true)) {
            reader.activateReadTimeout();
            if (request != null) {
                request.requestSent().run();
            }
        }
    }

    void cancelRequestBody() {
        cancelRequestBody(Http3ErrorCode.REQUEST_CANCELLED);
    }

    private void cancelRequestBody(Http3ErrorCode errorCode) {
        if (finished.get()
                || writer.sendingState().isTerminal()
                || !requestBodyCancelled.compareAndSet(false, true)) {
            return;
        }
        try {
            writer.reset(errorCode.code());
        } catch (QuicStreamException _) {
            // Request-body cancellation is best-effort once the stream is already failing or closing.
        }
    }

    void cancelResponseBody() {
        cancelResponseBody(Http3ErrorCode.REQUEST_CANCELLED);
    }

    private void cancelResponseBody(Http3ErrorCode errorCode) {
        if (finished.get() || !responseBodyCancelled.compareAndSet(false, true)) {
            return;
        }
        try {
            stream.requestStopSending(errorCode.code());
        } finally {
            responseCancellationHandler.accept(stream.streamId());
        }
    }

    private void responseProtocolFailure(Http3ProtocolException failure, CompletableFuture<Headers> trailers) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(trailers, "trailers").completeExceptionally(failure);
        if (failure.scope() == Http3ProtocolException.Scope.CONNECTION) {
            try {
                finish(StreamOutcome.ERROR);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            try {
                Objects.requireNonNull(session, "session").fail(stream.streamId(), failure);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            return;
        }
        try {
            cancelRequestBody(failure.errorCode());
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        try {
            cancelResponseBody(failure.errorCode());
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        try {
            finish((failure.errorCode() == Http3ErrorCode.REQUEST_REJECTED
                    || (stream.receivingState().isReset()
                    && stream.rcvErrorCode() == Http3ErrorCode.REQUEST_REJECTED.code()))
                           ? StreamOutcome.REJECTED
                           : StreamOutcome.RESET);
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    void finish(StreamOutcome outcome) {
        streamOutcome.compareAndSet(null, Objects.requireNonNull(outcome, "stream outcome"));
        if (!releaseRequested.compareAndSet(false, true)) {
            return;
        }
        if (executionClaimed.compareAndSet(false, true)) {
            response.completeExceptionally(
                    Http3RequestFailureSupport.attemptFailure(
                            Http3RequestFailureSupport.connectionRetired(),
                            NOT_PROCESSED,
                            false));
        }
        continueDecision.completeExceptionally(Http3RequestFailureSupport.connectionRetired());
        try {
            reader.close();
        } finally {
            Http3RequestExecution.Task currentResponseTask = responseTask.getAndSet(null);
            CompletableFuture<Void> responseTaskExited;
            if (currentResponseTask != null) {
                currentResponseTask.cancelAfterClose();
                responseTaskExited = currentResponseTask.exited();
            } else {
                responseTaskExited = CompletableFuture.completedFuture(null);
            }
            Http3RequestExecution.Task currentProducerTask = producerTask.getAndSet(null);
            CompletableFuture<Void> producerTaskExited;
            if (currentProducerTask != null) {
                currentProducerTask.cancelAfterClose();
                producerTaskExited = currentProducerTask.exited();
            } else {
                producerTaskExited = CompletableFuture.completedFuture(null);
            }
            CompletableFuture.allOf(sendCompletion, responseTaskExited, producerTaskExited)
                    .whenComplete((_, completionFailure) -> {
                        if (finished.compareAndSet(false, true)) {
                            StreamOutcome terminalOutcome = streamOutcome.get();
                            if (terminalOutcome == StreamOutcome.COMPLETED) {
                                if (completionFailure != null) {
                                    terminalOutcome = StreamOutcome.ERROR;
                                } else {
                                    QuicSenderStream.SendingStreamState terminalState = sendCompletion.getNow(null);
                                    if (terminalState != null
                                            && terminalState.isReset()
                                            && stream.sndErrorCode() != Http3ErrorCode.NO_ERROR.code()
                                            && stream.sndErrorCode()
                                            != Http3ErrorCode.REQUEST_CANCELLED.code()) {
                                        terminalOutcome = StreamOutcome.RESET;
                                    }
                                }
                            }
                            try {
                                streamObservation.close(terminalOutcome);
                            } finally {
                                completionHandler.accept(stream.streamId());
                            }
                        }
                    });
        }
    }

    private StreamOutcome failureOutcome(Throwable cause) {
        if (Http3RequestFailureSupport.isRetryableRequestFailure(cause)) {
            return StreamOutcome.REJECTED;
        }
        Throwable current = cause;
        while (current != null) {
            if (current instanceof QuicStreamException streamException
                    && streamException.kind() != QuicStreamException.Kind.RESET_LOCALLY
                    && streamException.errorCode().orElse(-1) == Http3ErrorCode.REQUEST_REJECTED.code()) {
                return StreamOutcome.REJECTED;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        if (stream.receivingState().isReset()) {
            return stream.rcvErrorCode() == Http3ErrorCode.REQUEST_REJECTED.code()
                    ? StreamOutcome.REJECTED
                    : StreamOutcome.RESET;
        }
        if (stream.stopSendingReceived()
                && stream.sndErrorCode() != Http3ErrorCode.NO_ERROR.code()
                && stream.sndErrorCode() != Http3ErrorCode.REQUEST_CANCELLED.code()) {
            return stream.sndErrorCode() == Http3ErrorCode.REQUEST_REJECTED.code()
                    ? StreamOutcome.REJECTED
                    : StreamOutcome.RESET;
        }
        return StreamOutcome.ERROR;
    }

    void cancel() {
        cancelRequestBody();
        cancelResponseBody();
        response.cancel(false);
        finish(StreamOutcome.CANCELLED);
    }

    void sessionClosed(Throwable cause, StreamOutcome outcome) {
        Throwable classified = classifyRequestFailure(Objects.requireNonNull(session, "session"), stream, cause);
        sessionCloseFailure.compareAndSet(null, classified);
        response.completeExceptionally(Http3RequestFailureSupport.attemptFailure(classified,
                                                                                  attemptDisposition(classified),
                                                                                  false));
        continueDecision.completeExceptionally(cause);
        try {
            cancelRequestBody();
        } catch (RuntimeException | Error cleanupFailure) {
            if (cause != cleanupFailure) {
                cause.addSuppressed(cleanupFailure);
            }
        }
        try {
            cancelResponseBody();
        } catch (RuntimeException | Error cleanupFailure) {
            if (cause != cleanupFailure) {
                cause.addSuppressed(cleanupFailure);
            }
        }
        finish(outcome);
    }

    CompletableFuture<Headers> responseTrailers() {
        return responseTrailers;
    }

    void completeTrailersFailure(Throwable failure) {
        Throwable canonicalFailure = sessionCloseFailure.get();
        responseTrailers.completeExceptionally(canonicalFailure == null
                                                        ? Objects.requireNonNull(failure, "failure")
                                                        : canonicalFailure);
    }

    private final class ReadTimeoutInputStream extends FilterInputStream {
        private final Http3StreamedResponse response;

        private ReadTimeoutInputStream(InputStream delegate, Http3StreamedResponse response) {
            super(delegate);
            this.response = response;
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (SocketTimeoutException timeout) {
                UncheckedIOException failure = new UncheckedIOException(timeout);
                response.readTimedOut(failure);
                throw failure;
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            try {
                return super.read(bytes, offset, length);
            } catch (SocketTimeoutException timeout) {
                UncheckedIOException failure = new UncheckedIOException(timeout);
                response.readTimedOut(failure);
                throw failure;
            }
        }

        @Override
        public long skip(long count) throws IOException {
            try {
                return super.skip(count);
            } catch (SocketTimeoutException timeout) {
                UncheckedIOException failure = new UncheckedIOException(timeout);
                response.readTimedOut(failure);
                throw failure;
            }
        }
    }

    private final class RequestOutputStream extends OutputStream {
        private final long contentLength = request.requestBody().contentLength().orElse(-1);
        private long bytesWritten;
        private int pendingDispatchBytes;
        private boolean headersSent;
        private volatile boolean interrupted;
        private volatile boolean closed;
        private volatile RuntimeException dispatchFailure;

        @Override
        public void write(int value) throws IOException {
            byte[] data = {(byte) value};
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, data.length);
            if (length == 0) {
                return;
            }
            ensureWritable();
            if (!headersSent) {
                sendHeaders(false, true);
            }
            ensureWritable();
            long newBytesWritten = bytesWritten + length;
            if (contentLength >= 0 && newBytesWritten > contentLength) {
                throw new IllegalStateException("Content length was set to " + contentLength
                                                        + ", but the request producer wrote " + newBytesWritten + " bytes");
            }
            int written = 0;
            while (written < length) {
                int chunkSize = Math.min(REQUEST_BUFFER_SIZE, length - written);
                pendingDispatchBytes += chunkSize;
                boolean awaitDispatch = pendingDispatchBytes >= REQUEST_DISPATCH_WINDOW_SIZE;
                dispatchRequest(Http3Protocol.encodeDataFrameBuffer(data, offset + written, chunkSize),
                                false,
                                awaitDispatch);
                if (awaitDispatch) {
                    pendingDispatchBytes = 0;
                }
                written += chunkSize;
            }
            bytesWritten = newBytesWritten;
        }

        @Override
        public void close() throws IOException {
            if (closed || interrupted || dispatchFailure != null) {
                return;
            }
            if (contentLength >= 0 && bytesWritten != contentLength) {
                throw new IllegalStateException("Content length was set to " + contentLength
                                                        + ", but the request producer wrote " + bytesWritten + " bytes");
            }
            if (!headersSent) {
                sendHeaders(true, false);
                closed = true;
                return;
            }
            requestCommitment.set(Http3RequestExecution.Commitment.REQUEST_DISPATCHING);
            dispatchRequest(BufferData.empty(), true, true);
            pendingDispatchBytes = 0;
            requestCommitment.set(Http3RequestExecution.Commitment.REQUEST_DISPATCHED);
            closed = true;
            signalRequestSent();
        }

        private void sendHeaders(boolean last, boolean hasData) {
            if (hasData
                    && request.sendExpectContinue()
                    && !request.headers().contains(HeaderValues.EXPECT_100)) {
                request.headers().set(HeaderValues.EXPECT_100);
            }
            URI uri = request.uri();
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (uri.getRawQuery() != null) {
                path += "?" + uri.getRawQuery();
            }
            Http3MessageReader.validateRequestHeaders(request.headers(), request.validateRequestHeaders());
            String authority = Http3Protocol.requestAuthority(uri, request.headers());
            boolean connect = request.method() == Method.CONNECT;
            sendFrameListener.requestHeaders(
                    session.connection(),
                    stream.streamId(),
                    request.method().text(),
                    connect ? "" : Objects.requireNonNullElse(uri.getScheme(), ""),
                    Objects.requireNonNullElse(authority, ""),
                    connect ? "" : path,
                    request.headers());
            byte[] requestHeaders = Http3Protocol.encodeRequestHeaders(
                    session.qpackContext(),
                    stream.streamId(),
                    uri,
                    request.method().text(),
                    request.headers());
            Http3RequestExecution.Commitment commitment = last
                    ? Http3RequestExecution.Commitment.REQUEST_DISPATCHING
                    : Http3RequestExecution.Commitment.COMMITTING;
            requestCommitment.set(commitment);
            dispatchRequest(BufferData.create(requestHeaders), last, true);
            commitment = last
                    ? Http3RequestExecution.Commitment.REQUEST_DISPATCHED
                    : Http3RequestExecution.Commitment.HEADERS_DISPATCHED;
            requestCommitment.set(commitment);
            headersSent = true;
            if (last) {
                signalRequestSent();
                return;
            }
            if (hasData && request.headers().containsToken(HeaderValues.EXPECT_100)) {
                Http3RequestExecution.ContinueDecision decision = continueDecision.completeOnTimeout(
                        Http3RequestExecution.ContinueDecision.SEND_BODY,
                        request.continueTimeout().toMillis(),
                        TimeUnit.MILLISECONDS)
                        .join();
                if (decision == Http3RequestExecution.ContinueDecision.RESPONSE_READY) {
                    interrupt();
                    cancelRequestBody();
                    signalRequestSent();
                    throw new Http3RequestExecution.BodyInterruptedException();
                }
            }
        }

        private void dispatchRequest(BufferData buffer, boolean last, boolean awaitDispatch) {
            try {
                dispatch(buffer, last, awaitDispatch);
            } catch (RuntimeException e) {
                if (stream.stopSendingReceived()
                        && (stream.sndErrorCode() == Http3ErrorCode.NO_ERROR.code()
                                || stream.sndErrorCode() == Http3ErrorCode.REQUEST_CANCELLED.code())) {
                    interrupt();
                    throw new Http3RequestExecution.BodyInterruptedException(e);
                }
                if (dispatchFailure == null) {
                    dispatchFailure = e;
                }
                throw e;
            }
        }

        private void ensureWritable() {
            if (closed) {
                throw new IllegalStateException("HTTP/3 request output stream is closed");
            }
            if (interrupted) {
                throw new Http3RequestExecution.BodyInterruptedException();
            }
        }

        private void interrupt() {
            interrupted = true;
        }

        private boolean interrupted() {
            return interrupted;
        }

        private boolean closed() {
            return closed;
        }
    }
}
