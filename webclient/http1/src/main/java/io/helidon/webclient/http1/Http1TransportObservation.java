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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.HttpTransportObserverSupport;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;

/**
 * One HTTP/1 exchange, retained when a redirect probe resumes its request body.
 */
final class Http1TransportObservation {
    private static final VarHandle CLOSED;

    static {
        try {
            CLOSED = MethodHandles.lookup().findVarHandle(Http1TransportObservation.class, "closed", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ClientConnection connection;
    private final StreamObservation stream;
    private volatile int closed;

    private Http1TransportObservation(ClientConnection connection, StreamObservation stream) {
        this.connection = connection;
        this.stream = stream;
    }

    static Http1TransportObservation open(ClientConnection connection) {
        ConnectionObservation observation = HttpTransportObserverSupport.connection(connection);
        if (observation == ConnectionObservation.noop()) {
            return null;
        }
        observation.protocolSelected(PROTOCOL_HTTP_1_1);
        return new Http1TransportObservation(connection,
                                             observation.streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL));
    }

    void responseHeaders(Method method, Status status, Headers headers) {
        if (Http1CallChainBase.isSuccessfulConnect(method, status)) {
            return;
        }
        if (status.family() == Status.Family.INFORMATIONAL && status != Status.SWITCHING_PROTOCOLS_101) {
            // An output-stream redirect probe can expose 100 Continue, then resume the same request.
            return;
        }
        if (method == Method.HEAD
                || status == Status.SWITCHING_PROTOCOLS_101
                || status == Status.NO_CONTENT_204
                || status == Status.NOT_MODIFIED_304
                || (!headers.contains(HeaderNames.TRANSFER_ENCODING) && headers.contentLength().orElse(-1) == 0)) {
            complete();
        }
    }

    InputStream inputStream(InputStream delegate) {
        return new FilterInputStream(delegate) {
            @Override
            public int read() throws IOException {
                try {
                    return in.read();
                } catch (IOException | RuntimeException e) {
                    fail(e);
                    throw e;
                }
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                try {
                    return in.read(bytes, offset, length);
                } catch (IOException | RuntimeException e) {
                    fail(e);
                    throw e;
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    in.close();
                } catch (IOException | RuntimeException e) {
                    fail(e);
                    throw e;
                }
            }
        };
    }

    void bodyComplete(boolean trailersPending) {
        if (!trailersPending) {
            complete();
        }
    }

    void remoteComplete() {
        complete();
        HttpTransportObserverSupport.connectionOutcome(connection, ConnectionOutcome.REMOTE_CLOSE);
    }

    void complete() {
        close(StreamOutcome.COMPLETED);
    }

    void cancel() {
        close(StreamOutcome.CANCELLED);
    }

    void fail(Throwable failure) {
        if (closed == 0 && (boolean) CLOSED.compareAndSet(this, 0, 1)) {
            HttpTransportObserverSupport.connectionFailed(connection, failure);
            stream.close(StreamOutcome.ERROR);
        }
    }

    private void close(StreamOutcome outcome) {
        if (closed == 0 && (boolean) CLOSED.compareAndSet(this, 0, 1)) {
            stream.close(outcome);
        }
    }
}
