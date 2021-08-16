/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.utils.SocketHttpClient.longData;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The BytesReuseTest verifies whether the {@link DataChunk} instances get released properly.
 * <p>
 * Note that with {@link DataChunk#finalize()} which calls {@link DataChunk#release()},
 * we no longer experience {@link OutOfMemoryError} exceptions in case the chunks aren't freed
 * as long as no references to the {@link DataChunk} instances are kept.
 */
public class BytesReuseTest {

    private static final Logger LOGGER = Logger.getLogger(PlainTest.class.getName());

    private static WebServer webServer;

    private static Queue<DataChunk> chunkReference = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r) {{
        setDaemon(true);
    }});

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     *             the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) throws Exception {
        webServer = WebServer.builder()
                .port(port)
                .routing(Routing.builder()
                                 .any((req, res) -> {
                                     req.content().registerFilter(
                                             (Publisher<DataChunk> publisher) -> Multi.create(publisher).map(chunk -> {
                                                 if (req.queryParams().first("keep_chunks").map(Boolean::valueOf).orElse(true)) {
                                                     chunkReference.add(chunk);
                                                 }
                                                 return chunk;
                                             }));
                                     res.headers().add("Transfer-Encoding", "chunked");
                                     req.next();
                                 })
                                 .post("/subscriber", (req, res) -> {
                                     Multi.create(req.content()).subscribe((DataChunk chunk) -> {
                                         if (req.queryParams().first("release").map(Boolean::valueOf).orElse(true)) {
                                             chunk.release();
                                         }
                                     }, (Throwable ex) -> {
                                         LOGGER.log(Level.WARNING,
                                                    "Encountered an exception!", ex);
                                         res.status(500)
                                                 .send("Error: " + ex.getMessage());
                                     }, () -> {
                                         res.send("Finished");
                                     }, (Subscription subscription) -> {
                                         subscription.request(Long.MAX_VALUE);
                                     });
                                 })
                                 .post("/string", Handler.create(String.class, (req, res, s) -> {
                                     assertAgainstPrefixQueryParam(req, s);
                                     res.send("Finished");
                                 }))
                                 .post("/bytes", Handler.create(byte[].class, (req, res, b) -> {
                                     assertAgainstPrefixQueryParam(req, new String(b));
                                     res.send("Finished");
                                 }))
                                 .post("/bytes_deferred", (req, res) -> {
                                     Executors.newSingleThreadExecutor().submit(() -> {
                                         req.content().as(byte[].class).thenAccept(bytes -> {
                                             assertAgainstPrefixQueryParam(req, new String(bytes));
                                             res.send("Finished");
                                         }).exceptionally(t -> {
                                             req.next(t);
                                             return null;
                                         });
                                     });
                                 })
                                 .post("/input_stream", Handler.create(InputStream.class, (req, res, stream) -> {
                                     Executors.newSingleThreadExecutor().submit(() -> {
                                         try {
                                             LOGGER.info("Consuming data from input stream!");
                                             assertAgainstPrefixQueryParam(req,
                                                                           new String(stream.readAllBytes()));
                                             res.send("Finished");
                                         } catch (IOException e) {
                                             req.next(new IllegalStateException("Got an IO error.", e));
                                         }
                                     });
                                 }))
                                 .any("/unconsumed", (req, res) -> res.send("Nothing consumed!"))
                                 .build())
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", webServer.port());
    }

    private static void assertAgainstPrefixQueryParam(ServerRequest req, String actual) {
        assertThat(actual,
                   startsWith(req.queryParams()
                                      .first("test")
                                      .orElseThrow(() -> new BadRequestException("Missing 'test' query param"))));
    }

    @BeforeAll
    public static void startServer() throws Exception {
        // start the server at a free port
        startServer(0);
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        chunkReference.clear();
    }

    private void doSubscriberPostRequest(boolean release) throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.POST, "/subscriber?test=myData&release=" + release, "myData" + longData(100_000).toString());
            assertThat(s.receive(), endsWith("\nFinished\n0\n\n"));
        }
    }

    private void assertChunkReferencesAreReleased() {
        LOGGER.log(Level.INFO, "Asserting that {0} request chunks were released.", chunkReference.size());
        for (DataChunk chunk : chunkReference) {
            assertThat("The chunk was not released: ID " + chunk.id(), chunk.isReleased(), is(true));
        }
    }

    @Test
    public void requestChunkDataRemainsWhenNotReleased() throws Exception {
        doSubscriberPostRequest(false);
        for (DataChunk chunk : chunkReference) {
            assertThat("The chunk was released: ID " + chunk.id(), chunk.isReleased(), is(false));
        }
        assertThat(new String(chunkReference.peek().bytes()), startsWith("myData"));
    }

    @Test
    @Disabled("This test takes minutes before it throws OutOfMemoryError")
    public void requestChunkDataRemainsWhenNotReleasedOutOfMemoryError() throws Exception {
        for (int i = 0; i < 100_000; i++) {
            try {
                requestChunkDataRemainsWhenNotReleased();
            } finally {
                LOGGER.log(Level.INFO, "Iteration reached: {0}", i);
            }
        }
        fail("An assertion was expected: OutOfMemoryError");
    }

    @Test
    public void requestChunkDataGetReusedWhenReleased() throws Exception {
        doSubscriberPostRequest(true);
        assertChunkReferencesAreReleased();
    }

    @Test
    @Disabled("The intention of this test is to show that webserver can run indefinitely")
    public void requestChunkDataGetReusedWhenReleasedDoesNeverFail() throws Exception {
        for (int i = 0; i < 100_000; i++) {
            try {
                requestChunkDataGetReusedWhenReleased();
            } finally {
                LOGGER.log(Level.INFO, "Iteration reached: {0}", i);
            }
        }
    }

    @Test
    public void toStringConverterFreesTheRequestChunks() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.POST, "/string?test=myData", "myData" + longData(100_000).toString());
            assertThat(s.receive(), endsWith("\nFinished\n0\n\n"));
        }
        assertChunkReferencesAreReleased();
    }

    @Test
    public void toByteArrayConverterFreesTheRequestChunks() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.POST, "/bytes?test=myData", "myData" + longData(100_000).toString());
            assertThat(s.receive(), endsWith("\nFinished\n0\n\n"));
        }
        assertChunkReferencesAreReleased();
    }

    @Test
    public void toByteArrayDeferredConverterFreesTheRequestChunks() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.POST, "/bytes_deferred?test=myData", "myData" + longData(100_000).toString());
            assertThat(s.receive(), endsWith("\nFinished\n0\n\n"));
        }
        assertChunkReferencesAreReleased();
    }

    @Test
    public void toInputStreamConverterFreesTheRequestChunks() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.POST, "/input_stream?test=myData", "myData" + longData(100_000).toString());
            assertThat(s.receive(), endsWith("\nFinished\n0\n\n"));
        }
        assertChunkReferencesAreReleased();
    }

    @Test
    public void notFoundPostRequestPayloadGetsReleased() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.POST, "/non_existent?test=myData", "myData" + longData(100_000).toString());
            assertThat(s.receive(), startsWith("HTTP/1.1 404 Not Found\n"));
        }
        assertChunkReferencesAreReleased();
    }

    @Test
    public void unconsumedPostRequestPayloadGetsReleased() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.POST, "/unconsumed?test=myData", "myData" + longData(100_000).toString());
            assertThat(s.receive(), endsWith("Nothing consumed!\n0\nConnection: close\n\n"));
        }
        assertChunkReferencesAreReleased();
    }

    /**
     * This test causes the WebServer to throw {@link OutOfMemoryError} by not releasing the
     * {@link DataChunk} instances.
     * It takes several seconds which is why it's disabled by default.
     * <p>
     * Note that since {@link ByteBufRequestChunk} releases the underlying {@link io.netty.buffer.ByteBuf} on
     * {@link Object#finalize()} call, the {@link OutOfMemoryError} never occurs in case that the
     * {@link #chunkReference} doesn't get filled.
     *
     * @throws Exception in case of an error
     */
    @Test
    @Disabled("Ignored because to get the OutOfMemoryError it takes more than 10 seconds.")
    public void sendUnlimitedDataWithoutReleasingTheChunksEndsWithOutOfMemoryError() throws Exception {
        try {
            flood(false, true, 0, true);
        } catch (OutOfMemoryError e) {
            // OOME is expected either here or anywhere else (Netty threads for instance)
        }

        // and yes, the memory where the first chunk is stored is left intact
        assertThat(new String(chunkReference.peek().bytes()), startsWith("unlimited"));
    }

    /**
     * This test shows that even when the {@link DataChunk#release()} isn't called, we don't get
     * {@link OutOfMemoryError} thanks to {@link DataChunk#finalize()} that calls
     * {@link DataChunk#release()} automatically. This test needs at least 1GB of heap.
     * <p>
     * This feature is not guarantied though.
     *
     * @throws Exception in case of an error
     */
    @Test
    @Disabled("Ignored because this test should run indefinitely.")
    public void sendUnlimitedDataWithoutReleasingTheChunksDoesntEndsWithOutOfMemoryErrorAsLongAsChunkRefsArentKept()
            throws Exception {
        flood(false, false, 0, true);
    }

    /**
     * This test shows that when the {@link DataChunk#release()} is called, WebServer can run
     * indefinitely and it performs perfectly while operating with low amount of memory.
     * This test needs 128m of heap.
     *
     * @throws Exception in case of an error
     */
    @Test
    @Disabled("Ignored because this test should run indefinitely.")
    public void sendUnlimitedDataAndReleasingTheChunksAllowToRunIndefinitely() throws Exception {
        flood(true, false, 0, true);
    }

    /**
     * This test shows that with an unlimited number of connections, each sending 1GB of data, no
     * memory leak occurs.
     *
     * @throws Exception in case of a problem
     */
    @Test
    @Disabled("Ignored because this test should run indefinitely.")
    public void sendLimitedDataInAnUnlimitedLoopAndReleasingTheChunksAllowToRunIndefinitely() throws Exception {
        while (true) {
            flood(true, false, 1_000, false);
        }
    }

    /**
     * This test shows that in case that {@link ByteBufRequestChunk#finalize()} is disabled, there would
     * remain unreleased {@link io.netty.buffer.ByteBuf} instances that the {@link HttpInitializer} should
     * take care of.
     *
     * @throws Exception in case of an error
     */
    @Test
    @Disabled("Ignored because this test should run indefinitely.")
    public void sendLimitedDataInAnUnlimitedLoopWithoutReleasingIt() throws Exception {
        while (true) {
            flood(false, false, 10, false);
        }
    }

    /**
     * This test shows that with a WebServer shutdown, no memory leak occurs.
     *
     * If {@link ReferenceHoldingQueue#shutdown()} is not called, the {@code DEFAULT} pool arena
     * of the {@link io.netty.buffer.PooledByteBufAllocator} would grow without any limits.
     *
     * @throws Exception in case of an error
     */
    @Test
    @Disabled("Ignored because this test should run indefinitely.")
    public void sendLimitedDataInAnUnlimitedLoopWithoutReleasingItToRecreatedWebServer() throws Exception {
        // recreate the WebServer 10x times
        for (int i = 0; i < 10; i++) {
            flood(false, false, 1_000, false);
            webServer.shutdown()
                    .toCompletableFuture()
                    .join();
            startServer();
        }
        webServer.shutdown().toCompletableFuture().join();

        Thread.currentThread().join();
    }

    private void flood(boolean release, boolean keepChunks, int limit, boolean doAssert) throws Exception {
        try (SocketHttpClient s = new ChunkedSocketHttpClient(BytesReuseTest.webServer, limit)) {
            s.request(Http.Method.POST, "/subscriber?test=unlimited&release=" + release + "&keep_chunks=" + keepChunks, null);

            // so we got a OutOfMemoryError
            if (doAssert) {
                assertThat(s.receive(), endsWith("\nError: Java heap space\n0\n\n"));
            }
        }
    }

    private static class ChunkedSocketHttpClient extends SocketHttpClient {
        private final long limit;
        private final AtomicLong sentData = new AtomicLong();

        public ChunkedSocketHttpClient(WebServer webServer, long limit) throws IOException {
            super(webServer);
            this.limit = limit;
        }

        @Override
        protected void sendPayload(PrintWriter pw, String payload) {
            pw.println("transfer-encoding: chunked");
            pw.println("");
            pw.println("9");
            pw.println("unlimited");

            ScheduledFuture<?> future = startMeasuring();

            try {
                String data = longData(1_000_000).toString();
                long i = 0;
                for (; !pw.checkError() && (limit == 0 || i < limit); ++i) {
                    pw.println(Integer.toHexString(data.length()));
                    pw.println(data);
                    pw.flush();

                    sentData.addAndGet(data.length());
                }
                LOGGER.info("Published chunks: " + i);
            } finally {
                future.cancel(true);
            }
        }

        ScheduledFuture<?> startMeasuring() {
            long startTime = System.nanoTime();
            Queue<Long> receivedDataShort = new LinkedList<>();

            return service.scheduleAtFixedRate(() -> {
                long l = sentData.get() / 1000000;
                receivedDataShort.add(l);
                long previous = l - (receivedDataShort.size() > 10 ? receivedDataShort.remove() : 0);
                long time = TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
                System.out.println("Sent bytes: " + sentData.get() / 1000_000 + " MB");
                System.out.println("SPEED: " + l / time + " MB/s");
                System.out.println("SHORT SPEED: " + previous / (time > 10 ? 10 : time) + " MB/s");
                System.out.println("====");
            }, 1, 1, TimeUnit.SECONDS);
        }
    }
}
