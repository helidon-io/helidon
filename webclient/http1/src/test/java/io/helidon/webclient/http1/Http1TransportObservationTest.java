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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderNames;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(20)
class Http1TransportObservationTest {
    @Test
    void firstTerminalOutcomeSurvivesRepeatedCompletionAndLateFailure() throws Exception {
        for (StreamOutcome first : List.of(StreamOutcome.COMPLETED, StreamOutcome.CANCELLED, StreamOutcome.ERROR)) {
            var observer = new RecordingProvider();
            try (var server = new RawServer(1, socket -> {
                readHead(socket);
                write(socket, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nbody");
                assertThat(socket.getInputStream().read(), is(-1));
            })) {
                Http1Client client = client(server, observer);
                try (var response = (Http1ClientResponseImpl) client.get().request()) {
                    Http1TransportObservation observation = response.transportObservation();
                    terminate(observation, first);
                    observation.complete();
                    observation.cancel();
                    observation.fail(new IOException("Late transport failure"));
                } finally {
                    client.closeResourceAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
                server.await();
            }
            var connection = observer.onlyConnection();
            assertThat("First terminal outcome wins", connection.outcomes(), is(List.of(first)));
            assertThat("Only a winning failure changes the physical outcome", connection.outcome,
                       is(first == StreamOutcome.ERROR ? ConnectionOutcome.ERROR : ConnectionOutcome.LOCAL_CLOSE));
            connection.assertClosed();
        }
    }

    @Test
    void concurrentTerminationRetainsOneOutcomeAndItsPhysicalFailureState() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nbody");
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client client = client(server, observer);
            try (var response = (Http1ClientResponseImpl) client.get().request();
                 var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Http1TransportObservation observation = response.transportObservation();
                var ready = new CountDownLatch(3);
                var start = new CountDownLatch(1);
                try {
                    var tasks = new ArrayList<Future<?>>();
                    for (StreamOutcome outcome : List.of(StreamOutcome.COMPLETED, StreamOutcome.CANCELLED, StreamOutcome.ERROR)) {
                        tasks.add(executor.submit(() -> {
                            ready.countDown();
                            start.await();
                            terminate(observation, outcome);
                            return null;
                        }));
                    }
                    assertThat("All termination attempts are ready", ready.await(10, TimeUnit.SECONDS), is(true));
                    start.countDown();
                    for (var task : tasks) {
                        task.get(10, TimeUnit.SECONDS);
                    }
                } finally {
                    start.countDown();
                }
            } finally {
                client.closeResourceAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
            server.await();
        }
        var connection = observer.onlyConnection();
        assertThat("Exactly one terminal stream outcome", connection.outcomes().size(), is(1));
        StreamOutcome winner = connection.outcomes().getFirst();
        assertThat("The winning stream failure determines the physical outcome", connection.outcome,
                   is(winner == StreamOutcome.ERROR ? ConnectionOutcome.ERROR : ConnectionOutcome.LOCAL_CLOSE));
        connection.assertClosed();
    }

    @Test
    void sharedClientsObserveOnePhysicalConnectionAndDelivered500Completes() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            for (int status : List.of(200, 500, 200)) {
                readHead(socket);
                write(socket, "HTTP/1.1 " + status + " Test\r\nContent-Length: 4\r\n\r\nbody");
            }
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client first = client(server, observer);
            Http1Client second = client(server, observer);
            try {
                consume(first, 200);
                consume(second, 500);
                first.closeResource();
                assertThat(observer.onlyConnection().closes.get(), is(0));
                consume(second, 200);
            } finally {
                first.closeResource();
                second.closeResource();
            }
            server.await();
        }
        var connection = observer.onlyConnection();
        assertThat(connection.outcomes(), is(List.of(StreamOutcome.COMPLETED,
                                                      StreamOutcome.COMPLETED,
                                                      StreamOutcome.COMPLETED)));
        assertThat(connection.events, is(List.of("protocol:http/1.1", "stream:open", "stream:COMPLETED",
                                                  "stream:open", "stream:COMPLETED", "stream:open",
                                                  "stream:COMPLETED", "connection:LOCAL_CLOSE")));
        assertThat(observer.starts.get(), is(1));
        assertThat(observer.stops.get(), is(1));
        connection.assertClosed();
    }

    @Test
    void separateObserverScopesDoNotReuseEachOthersConnection() throws Exception {
        var firstObserver = new RecordingProvider();
        var secondObserver = new RecordingProvider();
        try (var server = new RawServer(2, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client first = client(server, firstObserver);
            Http1Client second = client(server, secondObserver);
            try {
                try (var response = first.get().request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (var response = second.get().request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
            } finally {
                first.closeResource();
                second.closeResource();
            }
            server.await();
        }
        assertThat(firstObserver.onlyConnection().outcomes(), is(List.of(StreamOutcome.COMPLETED)));
        assertThat(secondObserver.onlyConnection().outcomes(), is(List.of(StreamOutcome.COMPLETED)));
        firstObserver.onlyConnection().assertClosed();
        secondObserver.onlyConnection().assertClosed();
    }

    @Test
    void truncatedEntityIsAnErrorAndUnreadEntityIsCancelled() throws Exception {
        for (boolean read : List.of(true, false)) {
            var observer = new RecordingProvider();
            try (var server = new RawServer(1, socket -> {
                readHead(socket);
                write(socket, "HTTP/1.1 200 OK\r\nContent-Length: 20\r\n\r\nshort");
            })) {
                Http1Client client = client(server, observer);
                try (var response = client.get().request()) {
                    if (read) {
                        assertThrows(RuntimeException.class, () -> response.entity().as(String.class));
                    }
                } finally {
                    client.closeResource();
                }
                server.await();
            }
            var connection = observer.onlyConnection();
            assertThat(connection.outcomes(), is(List.of(read ? StreamOutcome.ERROR : StreamOutcome.CANCELLED)));
            assertThat(connection.outcome, is(read ? ConnectionOutcome.ERROR : ConnectionOutcome.LOCAL_CLOSE));
            connection.assertClosed();
        }
    }

    @Test
    void responseTimeoutIsReportedBeforePhysicalClose() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 200 OK\r\nContent-Length: 20\r\n\r\nshort");
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client client = client(server, observer);
            try (var response = client.get().request()) {
                ((Http1ClientResponseImpl) response).connection().readTimeout(Duration.ofMillis(100));
                assertThrows(RuntimeException.class, () -> response.entity().as(String.class));
            } finally {
                client.closeResource();
            }
            server.await();
        }
        assertThat(observer.onlyConnection().outcomes(), is(List.of(StreamOutcome.ERROR)));
        assertThat(observer.onlyConnection().outcome, is(ConnectionOutcome.TIMEOUT));
        observer.onlyConnection().assertClosed();
    }

    @Test
    void malformedChunkFramingIsAnErrorDespiteServiceCompletion() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" + "f".repeat(257) + "\r\n");
        })) {
            Http1Client client = client(server, observer);
            try (var response = client.get().request()) {
                assertThrows(RuntimeException.class, () -> response.entity().as(String.class));
            } finally {
                client.closeResource();
            }
            server.await();
        }
        assertThat(observer.onlyConnection().outcomes(), is(List.of(StreamOutcome.ERROR)));
        observer.onlyConnection().assertClosed();
    }

    @Test
    void bodyConsumptionCancelsUnreadTrailersAtRelease() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nTrailer: X-Result\r\n\r\n"
                    + "4\r\nbody\r\n0\r\nX-Result: done\r\n\r\n");
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client client = client(server, observer);
            try (var response = client.get().request()) {
                assertThat(response.entity().as(String.class), is("body"));
                // Existing entity consumption releases the connection before lazy trailers are read.
                assertThat(observer.onlyConnection().outcomes(), is(List.of(StreamOutcome.CANCELLED)));
                assertThat(response.trailers().get(HeaderNames.create("X-Result")).get(), is("done"));
                assertThat(observer.onlyConnection().outcomes(), is(List.of(StreamOutcome.CANCELLED)));
            } finally {
                client.closeResource();
            }
            server.await();
        }
        observer.onlyConnection().assertClosed();
    }

    @Test
    void explicitNoContentTrailersCompleteBeforeRelease() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 205 Reset Content\r\nTransfer-Encoding: chunked\r\nTrailer: X-Result\r\n\r\n"
                    + "0\r\nX-Result: done\r\n\r\n");
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client client = client(server, observer);
            try (var response = client.get().request()) {
                assertThat(observer.onlyConnection().outcomes().isEmpty(), is(true));
                response.entity();
                assertThat(response.trailers().get(HeaderNames.create("X-Result")).get(), is("done"));
                assertThat(observer.onlyConnection().outcomes(), is(List.of(StreamOutcome.COMPLETED)));
            } finally {
                client.closeResource();
            }
            server.await();
        }
        observer.onlyConnection().assertClosed();
    }

    @Test
    void continueAndRedirectProbeRetainOnlyTheActualExchange() throws Exception {
        var observer = new RecordingProvider();
        var requests = new AtomicInteger();
        try (var server = new RawServer(2, socket -> {
            String head = readHead(socket);
            assertThat(head, containsString("Expect: 100-continue"));
            if (requests.getAndIncrement() == 0) {
                write(socket, "HTTP/1.1 307 Temporary Redirect\r\nLocation: /redirected\r\nContent-Length: 0\r\n\r\n");
            } else {
                assertThat(head, containsString("POST /redirected HTTP/1.1"));
                write(socket, "HTTP/1.1 100 Continue\r\n\r\n");
                assertThat(readHead(socket), is("4\r\nbody\r\n0\r\n\r\n"));
                write(socket, "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 4\r\n\r\nbody");
                assertThat(socket.getInputStream().read(), is(-1));
            }
        })) {
            Http1Client client = client(server, observer);
            try (var response = client.post().sendExpectContinue(true).outputStream(output -> {
                output.write("body".getBytes(StandardCharsets.US_ASCII));
                output.close();
            })) {
                assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
                assertThat(response.entity().as(String.class), is("body"));
            } finally {
                client.closeResource();
            }
            server.await();
        }
        assertThat(observer.connections.size(), is(2));
        for (var connection : observer.connections) {
            assertThat(connection.outcomes(), is(List.of(StreamOutcome.COMPLETED)));
            connection.assertClosed();
        }
    }

    @Test
    void normalRedirectObservesBothExchangesOnOneConnection() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 302 Found\r\nLocation: /redirected\r\nContent-Length: 0\r\n\r\n");
            assertThat(readHead(socket), containsString("GET /redirected HTTP/1.1"));
            write(socket, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nbody");
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client client = client(server, observer);
            try {
                consume(client, 200);
            } finally {
                client.closeResourceAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
            server.await();
        }
        assertThat(observer.onlyConnection().outcomes(), is(List.of(StreamOutcome.COMPLETED, StreamOutcome.COMPLETED)));
        observer.onlyConnection().assertClosed();
    }

    @Test
    void upgradeCompletesHttpExchangeAndHandsOffPhysicalConnection() throws Exception {
        var observer = new RecordingProvider();
        try (var server = new RawServer(1, socket -> {
            readHead(socket);
            write(socket, "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n");
            assertThat(socket.getInputStream().read(), is(-1));
        })) {
            Http1Client client = client(server, observer);
            try {
                UpgradeResponse response = client.get().upgrade("websocket");
                assertThat(response.isUpgraded(), is(true));
                response.response().close();
                assertThat(observer.onlyConnection().outcomes(), is(List.of(StreamOutcome.COMPLETED)));
                assertThat(observer.onlyConnection().closes.get(), is(0));
                response.connection().closeResource();
            } finally {
                client.closeResource();
            }
            server.await();
        }
        observer.onlyConnection().assertClosed();
    }

    private static void terminate(Http1TransportObservation observation, StreamOutcome outcome) {
        switch (outcome) {
            case COMPLETED -> observation.complete();
            case CANCELLED -> observation.cancel();
            case ERROR -> observation.fail(new IOException("Transport failure"));
            case REJECTED, RESET -> throw new IllegalArgumentException("Not an HTTP/1 terminal outcome: " + outcome);
        }
    }

    private static Http1Client client(RawServer server, RecordingProvider provider) {
        return Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .readTimeout(Duration.ofSeconds(5))
                .shareConnectionCache(true)
                .addService(provider)
                .build();
    }

    private static void consume(Http1Client client, int status) {
        try (var response = client.get().request()) {
            assertThat(response.status().code(), is(status));
            assertThat(response.entity().as(String.class), is("body"));
        }
    }

    private static String readHead(Socket socket) throws IOException {
        InputStream input = socket.getInputStream();
        var bytes = new ByteArrayOutputStream();
        int suffix = 0;
        while (suffix != 0x0d0a0d0a) {
            int value = input.read();
            if (value == -1) {
                throw new IOException("Connection closed before expected HTTP framing: " + bytes);
            }
            bytes.write(value);
            suffix = (suffix << 8) | value;
        }
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    private static void write(Socket socket, String data) throws IOException {
        socket.getOutputStream().write(data.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    @FunctionalInterface
    private interface SocketHandler {
        void handle(Socket socket) throws Exception;
    }

    private static final class RawServer implements AutoCloseable {
        private final ServerSocket socket;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final List<Future<?>> handlers = new CopyOnWriteArrayList<>();
        private final Future<?> accepting;

        private RawServer(int connections, SocketHandler handler) throws IOException {
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            socket.setSoTimeout(5000);
            accepting = executor.submit(() -> {
                for (int i = 0; i < connections; i++) {
                    Socket accepted = socket.accept();
                    accepted.setSoTimeout(5000);
                    handlers.add(executor.submit(() -> {
                        try (accepted) {
                            handler.handle(accepted);
                        }
                        return null;
                    }));
                }
                return null;
            });
        }

        private int port() {
            return socket.getLocalPort();
        }

        private void await() throws Exception {
            accepting.get(10, TimeUnit.SECONDS);
            for (Future<?> handler : handlers) {
                handler.get(10, TimeUnit.SECONDS);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
            executor.shutdownNow();
            executor.close();
        }
    }

    private static final class RecordingProvider implements WebClientService, ObserverProvider {
        private final List<RecordingConnection> connections = new CopyOnWriteArrayList<>();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();

        private RecordingConnection onlyConnection() {
            assertThat(connections.size(), is(1));
            return connections.getFirst();
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            throw new AssertionError("Transport-only provider must not enter the request service chain");
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Object scope() {
            return this;
        }

        @Override
        public ObserverLifecycle createObserver() {
            return new ObserverLifecycle() {
                @Override
                public HttpTransportObserver start() {
                    starts.incrementAndGet();
                    return (role, transport, handshake) -> {
                        assertThat(role, is(Role.CLIENT));
                        assertThat(transport, is(HttpTransportObserver.TRANSPORT_TCP));
                        assertThat(handshake, is(Handshake.NONE));
                        var connection = new RecordingConnection();
                        connections.add(connection);
                        return connection;
                    };
                }

                @Override
                public CompletionStage<Void> stop() {
                    stops.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
            };
        }
    }

    private static final class RecordingConnection implements ConnectionObservation {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final List<RecordingStream> streams = new CopyOnWriteArrayList<>();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger duplicates = new AtomicInteger();
        private volatile ConnectionOutcome outcome;
        private String protocol;

        private List<StreamOutcome> outcomes() {
            return streams.stream().filter(stream -> stream.outcome != null).map(stream -> stream.outcome).toList();
        }

        private void assertClosed() {
            assertThat(closes.get(), is(1));
            assertThat(duplicates.get(), is(0));
            assertThat(streams.stream().allMatch(stream -> stream.closed.get()), is(true));
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            throw new AssertionError("A cleartext connection must not report a TLS handshake");
        }

        @Override
        public void protocolSelected(String protocol) {
            if (!protocol.equals(this.protocol)) {
                events.add("protocol:" + protocol);
                this.protocol = protocol;
            }
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            assertThat(protocol, is(HttpTransportObserver.PROTOCOL_HTTP_1_1));
            assertThat(direction, is(Direction.BIDIRECTIONAL));
            assertThat(initiator, is(Initiator.LOCAL));
            events.add("stream:open");
            var stream = new RecordingStream();
            streams.add(stream);
            return stream;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            closes.incrementAndGet();
            this.outcome = outcome;
            for (var stream : streams) {
                if (!stream.closed.get()) {
                    stream.close(outcome == ConnectionOutcome.ERROR || outcome == ConnectionOutcome.TIMEOUT
                                         ? StreamOutcome.ERROR : StreamOutcome.CANCELLED);
                }
            }
            events.add("connection:" + outcome);
        }

        private final class RecordingStream implements StreamObservation {
            private final AtomicBoolean closed = new AtomicBoolean();
            private volatile StreamOutcome outcome;

            @Override
            public void close(StreamOutcome outcome) {
                if (closed.compareAndSet(false, true)) {
                    this.outcome = outcome;
                    events.add("stream:" + outcome);
                } else {
                    duplicates.incrementAndGet();
                }
            }
        }
    }
}
