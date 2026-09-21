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

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import io.helidon.http.http3.Http3GoAway;

/**
 * Request-failure classification for the HTTP/3 web client.
 */
final class Http3RequestFailureSupport {
    private Http3RequestFailureSupport() {
    }

    /**
     * Classify a request failure using stream- and connection-level HTTP/3 state.
     *
     * @param cause underlying failure
     * @param requestRejectedByPeer whether the peer rejected the request through a stream action
     * @param versionFallbackByPeer whether the peer requested version fallback through a stream action
     * @param rejectsStream whether the current GOAWAY state rejects the request stream
     * @return classified failure
     */
    static Throwable classify(Throwable cause,
                              boolean requestRejectedByPeer,
                              boolean versionFallbackByPeer,
                              boolean rejectsStream) {
        Objects.requireNonNull(cause, "cause");
        if (isRetryable(cause)) {
            return cause;
        }
        if (versionFallbackByPeer) {
            return new VersionFallbackException(cause);
        }
        if (requestRejectedByPeer) {
            return new RequestRejectedException(cause);
        }
        if (rejectsStream) {
            return new ConnectionRetiredException(cause);
        }
        return cause;
    }

    /**
     * Create a retryable connection-retired failure used before a request stream is accepted.
     *
     * @return connection-retired failure
     */
    static IllegalStateException connectionRetired() {
        return new ConnectionRetiredException();
    }

    /**
     * Whether a failure carries an explicit retry or version-fallback action.
     *
     * @param throwable failure
     * @return {@code true} when the request can be attempted again using the required protocol action
     */
    static boolean isRetryable(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return isRetryableRequestFailure(cause) || cause instanceof VersionFallbackException;
    }

    /**
     * Whether a failure is retryable on a different connection.
     *
     * @param throwable failure
     * @return {@code true} when the failure is a generic retryable HTTP/3 request failure
     */
    static boolean isRetryableRequestFailure(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause instanceof ConnectionRetiredException
                || cause instanceof RequestRejectedException;
    }

    static RequestAttemptException attemptFailure(Throwable throwable,
                                                  AttemptDisposition disposition,
                                                  boolean sessionReusable) {
        Objects.requireNonNull(disposition, "disposition");
        Throwable cause = unwrap(throwable);
        Throwable endpointCause = null;
        Throwable current = cause;
        while (current != null) {
            if (current instanceof EndpointUnavailableException) {
                endpointCause = current.getCause() == null ? current : current.getCause();
                break;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        boolean endpointFailure = endpointCause != null;
        Throwable attemptCause = endpointFailure ? endpointCause : cause;
        return new RequestAttemptException(disposition, sessionReusable, endpointFailure, attemptCause);
    }

    static IllegalStateException endpointUnavailable(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        return cause instanceof EndpointUnavailableException endpointUnavailableException
                ? endpointUnavailableException
                : new EndpointUnavailableException(cause);
    }

    static AttemptDisposition attemptDisposition(Throwable throwable) {
        Throwable cause = unwrapCompletion(throwable);
        if (cause instanceof RequestAttemptException requestAttemptException) {
            return requestAttemptException.disposition();
        }
        return AttemptDisposition.POSSIBLY_PROCESSED;
    }

    static boolean isVersionFallback(Throwable throwable) {
        return unwrap(throwable) instanceof VersionFallbackException;
    }

    static Throwable attemptCause(Throwable throwable) {
        Throwable cause = unwrapCompletion(throwable);
        return cause instanceof RequestAttemptException requestAttemptException
                ? requestAttemptException.getCause()
                : cause;
    }

    static boolean sessionReusable(Throwable throwable) {
        Throwable cause = unwrapCompletion(throwable);
        return cause instanceof RequestAttemptException requestAttemptException
                && requestAttemptException.sessionReusable();
    }

    static boolean endpointFailure(Throwable throwable) {
        Throwable cause = unwrapCompletion(throwable);
        return cause instanceof RequestAttemptException requestAttemptException
                && requestAttemptException.endpointFailure();
    }

    /**
     * Whether the observed GOAWAY no longer permits a request stream id.
     *
     * @param goAway most recent peer GOAWAY, or {@code null} when none was observed
     * @param streamId request stream id
     * @return {@code true} when the stream id is rejected by GOAWAY
     */
    static boolean goAwayRejectsStream(Http3GoAway goAway, long streamId) {
        return goAway != null && goAway.rejectsStream(streamId);
    }

    static String throwableSummary(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    static void addSuppressed(Throwable cause, Throwable cleanupFailure) {
        if (cause != cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    static Throwable collectCleanupFailure(Throwable current, Throwable next) {
        if (current == null) {
            return next;
        }
        addSuppressed(current, next);
        return current;
    }

    static Throwable completionFailure(CompletionStage<?> completion) {
        Objects.requireNonNull(completion, "completion");
        return completion.handle((_, failure) -> failure).toCompletableFuture().join();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = unwrapCompletion(throwable);
        if (cause instanceof RequestAttemptException requestAttemptException) {
            return requestAttemptException.getCause();
        }
        return cause;
    }

    private static Throwable unwrapCompletion(Throwable throwable) {
        return throwable instanceof CompletionException completionException && completionException.getCause() != null
                ? completionException.getCause()
                : throwable;
    }

    enum AttemptDisposition {
        NOT_PROCESSED,
        VERSION_FALLBACK,
        POSSIBLY_PROCESSED
    }

    /**
     * Failure indicating the connection no longer accepts new requests.
     */
    static final class ConnectionRetiredException extends IllegalStateException {
        private ConnectionRetiredException() {
            super("HTTP/3 connection no longer accepts new requests.");
        }

        private ConnectionRetiredException(Throwable cause) {
            super("HTTP/3 connection no longer accepts new requests.", cause);
        }
    }

    /**
     * Failure indicating the request was rejected before processing.
     */
    static class RequestRejectedException extends IllegalStateException {
        private RequestRejectedException(String message, Throwable cause) {
            super(message, cause);
        }

        private RequestRejectedException(Throwable cause) {
            this("HTTP/3 request was rejected before processing.", cause);
        }
    }

    /**
     * Failure indicating that the request must fall back from HTTP/3 to HTTP/1.1.
     */
    static final class VersionFallbackException extends IllegalStateException {
        private VersionFallbackException(Throwable cause) {
            super("HTTP/3 peer requested version fallback.", cause);
        }
    }

    static final class RequestAttemptException extends IllegalStateException {
        private final AttemptDisposition disposition;
        private final boolean sessionReusable;
        private final boolean endpointFailure;

        private RequestAttemptException(AttemptDisposition disposition,
                                        boolean sessionReusable,
                                        boolean endpointFailure,
                                        Throwable cause) {
            super("HTTP/3 request attempt failed (" + disposition + ").", cause);
            this.disposition = disposition;
            this.sessionReusable = sessionReusable;
            this.endpointFailure = endpointFailure;
        }

        AttemptDisposition disposition() {
            return disposition;
        }

        boolean sessionReusable() {
            return sessionReusable;
        }

        boolean endpointFailure() {
            return endpointFailure;
        }
    }

    private static final class EndpointUnavailableException extends IllegalStateException {
        private EndpointUnavailableException(Throwable cause) {
            super("HTTP/3 endpoint is unavailable.", cause);
        }
    }

}
