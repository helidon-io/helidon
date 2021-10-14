/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import io.helidon.common.LazyValue;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.Multi;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BackpressureTest {

    private static final Logger LOGGER = Logger.getLogger(BackpressureTest.class.getName());
    static final long TIMEOUT_SEC = 40;
    // 5 MB buffer size should be enough to cause incomplete write in Netty
    static final int BUFFER_SIZE = 5 * 1024 * 1024;

    @Test
    void overloadEventLoopWithIoMulti() {
        Multi<DataChunk> pub = IoMulti.multiFromStreamBuilder(randomEndlessIs())
                .byteBufferSize(BUFFER_SIZE)
                .build()
                .map(byteBuffer -> DataChunk.create(true, byteBuffer));
        overloadEventLoop(pub);
    }

    @Test
    void overloadEventLoopWithMulti() {
        InputStream inputStream = randomEndlessIs();
        Multi<DataChunk> pub = Multi.create(() -> new Iterator<DataChunk>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public DataChunk next() {
                try {
                    return DataChunk.create(true, false, ByteBuffer.wrap(inputStream.readNBytes(BUFFER_SIZE)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        overloadEventLoop(pub);
    }

    /**
     * Attempts to overload webserver subscriber with higher data flow than Netty's NioEventLoop can
     * send at first iteration. By causing incomplete write leaves the rest of the bytebuffer to be written by the next
     * event loop iteration.
     * <p>
     * This can overflow Netty buffer or, in case of single threaded unbounded request, prevent event loop from ever reaching next
     * iteration.
     * <p>
     * Incomplete write is not flushed and its ChannelFuture's listener isn't executed, leaving DataChunk NOT released.
     * That should lead to OutOfMemory error or assertion error in sample DataChunk batch,
     * depends on the JVM memory settings.
     *
     * @param multi publisher providing endless stream of high volume(preferably more than 2 MB but not less than 1264 kB) data chunks
     */
    void overloadEventLoop(Multi<DataChunk> multi) {
        AtomicBoolean firstChunk = new AtomicBoolean(true);
        AtomicBoolean shuttingDown = new AtomicBoolean(false);
        AtomicReference<Optional<Throwable>> serverUpstreamError = new AtomicReference<>(Optional.empty());
        List<DataChunk> firstBatch = new ArrayList<>(5);

        Multi<DataChunk> dataChunkMulti =
                // Kill server publisher when client is done
                multi.takeWhile(ch -> !shuttingDown.get())
                        .peek(chunk -> {
                            if (firstChunk.getAndSet(false)) {
                                // skip first chunk, it gets released on complete
                                return;
                            }
                            // Keep 2 - 6 chunk references
                            if (firstBatch.size() < 5) {
                                firstBatch.add(chunk);
                            }
                        })
                        .onError(Throwable::printStackTrace)
                        .onError(t -> serverUpstreamError.set(Optional.of(t)));

        AtomicLong byteCnt = new AtomicLong();

        LazyValue<Boolean> validateOnce = LazyValue.create(() -> {
            Collection<DataChunk> snapshot = Collections.unmodifiableCollection(firstBatch);
            LOGGER.info("======== DataChunk sample batch ========");
            IntStream.range(0, snapshot.size())
                    .forEach(i -> LOGGER.info("Chunk #" + (i + 2) + " released: " + firstBatch.get(i).isReleased()));

            boolean result = firstBatch.stream()
                    .allMatch(DataChunk::isReleased);

            // clean up
            firstBatch.forEach(DataChunk::release);

            return result;
        });

        WebServer webServer = null;
        try {
            webServer = WebServer.builder()
                    .host("localhost")
                    .routing(Routing.builder()
                            .get("/", (req, res) -> res.send(dataChunkMulti))
                            .build())
                    .build()
                    .start()
                    .await(TIMEOUT_SEC, TimeUnit.SECONDS);


            WebClient.builder()
                    .baseUri("http://localhost:" + webServer.port())
                    .build()
                    .get()
                    .path("/")
                    .request()
                    .peek(res -> assertThat(res.status().reasonPhrase(), res.status().code(), is(200)))
                    .flatMap(WebClientResponse::content)
                    // limit reception to 300 MB
                    .takeWhile(ws -> byteCnt.get() < (300 * 1024 * 1024))
                    .forEach(chunk -> {
                        long actCnt = byteCnt.addAndGet(chunk.bytes().length);
                        if (actCnt % (100 * 1024 * 1024) == 0) {
                            LOGGER.info("Client received " + (actCnt / (1024 * 1024)) + "MB");
                        }
                        if (actCnt > (200 * 1024 * 1024)) {
                            // After 200 MB check fist 5 chunks if those are released
                            // but keep the pressure and don't kill the stream
                            assertThat("Not all chunks from the first batch are released!", validateOnce.get());
                        }
                        chunk.release();
                    })
                    // Kill server publisher we are done here
                    .onTerminate(() -> shuttingDown.set(true))
                    .await(TIMEOUT_SEC, TimeUnit.SECONDS);

        } finally {
            if (webServer != null) {
                webServer.shutdown().await(TIMEOUT_SEC, TimeUnit.SECONDS);
            }
        }
        serverUpstreamError.get().ifPresent(Assertions::fail);
    }

    static InputStream randomEndlessIs() {
        Random random = new Random();
        return new InputStream() {
            @Override
            public synchronized int read() {
                return random.nextInt(Byte.MAX_VALUE);
            }
        };
    }
}
