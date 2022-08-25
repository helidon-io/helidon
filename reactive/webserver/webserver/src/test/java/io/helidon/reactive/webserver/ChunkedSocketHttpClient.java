/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.testing.http.junit5.SocketHttpClient;

class ChunkedSocketHttpClient extends SocketHttpClient {
    private static final System.Logger LOGGER = System.getLogger(ChunkedSocketHttpClient.class.getName());
    private static final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r) {{
        setDaemon(true);
    }});

    private final long limit;
    private final AtomicLong sentData = new AtomicLong();

    public ChunkedSocketHttpClient(WebServer webServer, long limit) {
        super("localhost", webServer.port(), Duration.ofSeconds(5));
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
            LOGGER.log(System.Logger.Level.INFO, "Published chunks: " + i);
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
