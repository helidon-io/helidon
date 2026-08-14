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

package io.helidon.http;

import java.util.List;

import io.helidon.common.Api;

/**
 * Protocol-neutral observation of HTTP transport lifecycle.
 *
 * <p>Callbacks must not block. Unless a lifecycle method specifies stricter publisher ordering, callbacks may be invoked
 * concurrently, so implementations must be thread safe.
 * Observation must not affect protocol or transport behavior. Observer callbacks are passive and must not directly or
 * indirectly invoke lifecycle methods on a {@link ConnectionObservation}, {@link HandshakeObservation}, or
 * {@link StreamObservation}; callback reentry is not supported.
 */
@Api.Internal
@FunctionalInterface
public interface HttpTransportObserver {
    /**
     * Known transport identifier for TCP.
     */
    String TRANSPORT_TCP = "tcp";
    /**
     * Known transport identifier for Unix domain sockets.
     */
    String TRANSPORT_UNIX = "unix";
    /**
     * Known transport identifier for QUIC.
     */
    String TRANSPORT_QUIC = "quic";
    /**
     * Known protocol identifier for HTTP/1.1, aligned with the HTTP transport metrics tag value.
     */
    String PROTOCOL_HTTP_1_1 = "http/1.1";
    /**
     * Known protocol identifier for HTTP/2, aligned with the HTTP transport metrics tag value.
     */
    String PROTOCOL_HTTP_2 = "http/2";
    /**
     * Known protocol identifier for HTTP/3, aligned with the HTTP transport metrics tag value.
     */
    String PROTOCOL_HTTP_3 = "http/3";

    /**
     * Returns an observer which ignores all lifecycle events.
     *
     * @return no-op observer
     */
    static HttpTransportObserver noop() {
        return HttpTransportObservers.NOOP;
    }

    /**
     * Combines observers into one failure-isolating observer.
     *
     * <p>Duplicate observer instances are included only once. A {@link RuntimeException} from one observer is logged and
     * does not prevent the remaining observers from receiving the event. Any other {@link Throwable} is rethrown;
     * a connection transition failure is rethrown after transitions already queued for that connection complete.
     *
     * @param observers observers to combine
     * @return combined observer
     */
    static HttpTransportObserver compose(List<? extends HttpTransportObserver> observers) {
        return HttpTransportObservers.compose(observers);
    }

    /**
     * Observes an opened physical connection.
     *
     * <p>The caller owns the returned observation and closes it when the connection terminates.
     * The returned observation must not be {@code null}.
     *
     * @param role endpoint role
     * @param transport non-blank physical transport identifier
     * @param handshake connection handshake type
     * @return connection observation
     */
    ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake);

    /**
     * Endpoint role.
     */
    enum Role {
        /**
         * Client endpoint.
         */
        CLIENT,
        /**
         * Server endpoint.
         */
        SERVER
    }

    /**
     * Connection handshake.
     */
    enum Handshake {
        /**
         * No security handshake.
         */
        NONE,
        /**
         * TLS over a stream transport.
         */
        TLS,
        /**
         * TLS integrated with QUIC.
         */
        QUIC_TLS
    }

    /**
     * Handshake completion outcome.
     */
    enum HandshakeOutcome {
        /**
         * Handshake succeeded.
         */
        SUCCESS,
        /**
         * Handshake failed.
         */
        FAILURE,
        /**
         * Handshake was cancelled because the connection closed.
         */
        CANCELLED,
        /**
         * Handshake timed out.
         */
        TIMEOUT
    }

    /**
     * Connection termination outcome.
     *
     * <p>Publishers must classify a timeout as {@link #TIMEOUT} and any other failure as {@link #ERROR}. Otherwise,
     * an endpoint-directed close is {@link #LOCAL_CLOSE} or {@link #REMOTE_CLOSE}, including when the close is graceful.
     * {@link #NORMAL} is reserved for graceful completion without an applicable endpoint-directed close.
     */
    enum ConnectionOutcome {
        /**
         * Connection completed gracefully without an applicable endpoint-directed close.
         */
        NORMAL,
        /**
         * Local endpoint initiated the connection close.
         */
        LOCAL_CLOSE,
        /**
         * Remote endpoint initiated the connection close.
         */
        REMOTE_CLOSE,
        /**
         * Connection termination was caused by a timeout.
         */
        TIMEOUT,
        /**
         * Connection termination was caused by an error other than a timeout.
         */
        ERROR
    }

    /**
     * Stream termination outcome.
     *
     * <p>{@link #REJECTED} takes precedence over {@link #RESET} when a stream is refused before application processing,
     * including when the refusal is conveyed using reset signaling. {@link #RESET} applies to other explicit resets,
     * and {@link #CANCELLED} applies when the stream lifecycle is cancelled without a reset or stream failure.
     */
    enum StreamOutcome {
        /**
         * Stream completed normally.
         */
        COMPLETED,
        /**
         * Stream was rejected before application processing.
         */
        REJECTED,
        /**
         * Stream was explicitly reset without known rejection semantics.
         */
        RESET,
        /**
         * Stream lifecycle was cancelled without an explicit reset or stream failure.
         */
        CANCELLED,
        /**
         * Stream ended because of a non-reset error.
         */
        ERROR
    }

    /**
     * Stream directionality.
     */
    enum Direction {
        /**
         * Bidirectional stream.
         */
        BIDIRECTIONAL,
        /**
         * Unidirectional stream.
         */
        UNIDIRECTIONAL
    }

    /**
     * Stream initiator relative to the observing endpoint.
     */
    enum Initiator {
        /**
         * Locally initiated stream.
         */
        LOCAL,
        /**
         * Remotely initiated stream.
         */
        REMOTE
    }

    /**
     * Observation of one physical connection.
     */
    interface ConnectionObservation {
        /**
         * Returns a no-op connection observation.
         *
         * @return no-op observation
         */
        static ConnectionObservation noop() {
            return HttpTransportObservers.NOOP_CONNECTION;
        }

        /**
         * Observes the start of the configured handshake.
         *
         * <p>The publisher must not invoke this method concurrently for the same connection; concurrent invocation is
         * unsupported. The same observation is returned if this method is invoked more than once sequentially.
         * The returned observation must not be {@code null}. Implementations that do not observe handshakes should
         * return {@link HandshakeObservation#noop()}.
         *
         * @return handshake observation
         */
        HandshakeObservation handshakeStarted();

        /**
         * Reports the currently selected HTTP protocol.
         *
         * <p>The first selection marks the physical connection as established and usable for that protocol. A publisher
         * must therefore report the first selection only after any required transport security handshake has succeeded
         * and its {@link HandshakeObservation#close(HandshakeOutcome)} call has returned. The selected protocol may later
         * change, for example after a successful HTTP/1.1 upgrade to HTTP/2. Repeated selection of an equal protocol
         * identifier has no effect.
         *
         * @param protocol non-blank selected protocol identifier
         */
        void protocolSelected(String protocol);

        /**
         * Observes a started application request and response exchange.
         *
         * <p>An HTTP/1.1 publisher reports each exchange once as a {@link Direction#BIDIRECTIONAL} stream, with
         * {@link Initiator} identifying the request sender relative to the observed endpoint. HTTP/2 and HTTP/3
         * publishers apply the same mapping to request streams. Publishers must not report server-push streams,
         * protocol control streams, compression-state streams such as HTTP/3 QPACK encoder or decoder streams, or
         * underlying transport streams.
         *
         * <p>The publisher must report the initial protocol selection before invoking this method. For a secured
         * transport, this means after the required security handshake has succeeded. This lifecycle model does not
         * represent HTTP streams accepted as TLS or QUIC early data.
         *
         * <p>The caller owns the returned observation and closes it when the stream terminates.
         * The returned observation must not be {@code null}.
         *
         * @param direction stream directionality
         * @param initiator stream initiator
         * @return stream observation
         */
        StreamObservation streamOpened(Direction direction, Initiator initiator);

        /**
         * Completes this connection observation.
         *
         * <p>This method is idempotent. It also completes any handshake or stream observations which are still open.
         * Open child observations receive these outcomes:
         * <table>
         *     <caption>Outcomes for open child observations</caption>
         *     <tr><th>Connection outcome</th><th>Handshake outcome</th><th>Stream outcome</th></tr>
         *     <tr><td>{@link ConnectionOutcome#NORMAL}</td><td>{@link HandshakeOutcome#CANCELLED}</td>
         *         <td>{@link StreamOutcome#CANCELLED}</td></tr>
         *     <tr><td>{@link ConnectionOutcome#LOCAL_CLOSE}</td><td>{@link HandshakeOutcome#CANCELLED}</td>
         *         <td>{@link StreamOutcome#CANCELLED}</td></tr>
         *     <tr><td>{@link ConnectionOutcome#REMOTE_CLOSE}</td><td>{@link HandshakeOutcome#FAILURE}</td>
         *         <td>{@link StreamOutcome#CANCELLED}</td></tr>
         *     <tr><td>{@link ConnectionOutcome#TIMEOUT}</td><td>{@link HandshakeOutcome#TIMEOUT}</td>
         *         <td>{@link StreamOutcome#ERROR}</td></tr>
         *     <tr><td>{@link ConnectionOutcome#ERROR}</td><td>{@link HandshakeOutcome#FAILURE}</td>
         *         <td>{@link StreamOutcome#ERROR}</td></tr>
         * </table>
         *
         * @param outcome termination outcome
         */
        void close(ConnectionOutcome outcome);
    }

    /**
     * Observation of one security handshake.
     */
    @FunctionalInterface
    interface HandshakeObservation {
        /**
         * Returns a no-op handshake observation.
         *
         * @return no-op observation
         */
        static HandshakeObservation noop() {
            return HttpTransportObservers.NOOP_HANDSHAKE;
        }

        /**
         * Completes this handshake observation.
         *
         * <p>This method is idempotent.
         *
         * @param outcome handshake outcome
         */
        void close(HandshakeOutcome outcome);
    }

    /**
     * Observation of one application request and response exchange.
     */
    @FunctionalInterface
    interface StreamObservation {
        /**
         * Returns a no-op stream observation.
         *
         * @return no-op observation
         */
        static StreamObservation noop() {
            return HttpTransportObservers.NOOP_STREAM;
        }

        /**
         * Completes this stream observation.
         *
         * <p>This method is idempotent.
         *
         * @param outcome stream outcome
         */
        void close(StreamOutcome outcome);
    }
}
