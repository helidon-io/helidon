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
 * <p>Callbacks must not block. They may be invoked concurrently, so implementations must be thread safe.
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
     * <p>Duplicate observer instances are included only once. A non-fatal failure from one observer is logged and does
     * not prevent the remaining observers from receiving the event. A {@link VirtualMachineError} is propagated
     * immediately.
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
     */
    enum ConnectionOutcome {
        /**
         * Connection completed normally.
         */
        NORMAL,
        /**
         * Local endpoint closed the connection.
         */
        LOCAL_CLOSE,
        /**
         * Remote endpoint closed the connection.
         */
        REMOTE_CLOSE,
        /**
         * Connection timed out.
         */
        TIMEOUT,
        /**
         * Connection ended because of an error.
         */
        ERROR
    }

    /**
     * Stream termination outcome.
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
         * Stream was reset.
         */
        RESET,
        /**
         * Stream was cancelled without an explicit reset event.
         */
        CANCELLED,
        /**
         * Stream ended because of an error.
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
         * <p>The same observation is returned if this method is invoked more than once.
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
         * must therefore report the first selection only after any required transport security handshake has succeeded.
         * The selected protocol may later change, for example after a successful HTTP/1.1 upgrade to HTTP/2. Repeated
         * selection of an equal protocol identifier has no effect.
         *
         * @param protocol non-blank selected protocol identifier
         */
        void protocolSelected(String protocol);

        /**
         * Observes an opened HTTP stream.
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
     * Observation of one multiplexed HTTP stream.
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
