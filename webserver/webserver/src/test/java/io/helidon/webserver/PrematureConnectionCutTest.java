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
 *
 */

package io.helidon.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.anyOf;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;

import static org.hamcrest.MatcherAssert.assertThat;

public class PrematureConnectionCutTest {

    private static final Duration TIMEOUT = Duration.of(3, ChronoUnit.SECONDS);

    @Test
    void cutConnectionBefore100Continue() throws Exception {
        CountDownLatch latch = new CountDownLatch(5);

        TestThreadFactory threadFactory = new TestThreadFactory();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        WebServer webServer = null;
        try {
            webServer = WebServer.builder()
                    .port(0)
                    .routing(Routing.builder()
                            .post((req, res) -> req.content()
                                    .as(InputStream.class)
                                    .thenAccept(is -> {
                                        threadFactory.newThread(() -> {
                                            try {
                                                is.readAllBytes(); // this is where thread could get blocked indefinitely
                                                res.send();
                                            } catch (IOException e) {
                                                exceptions.add(e);
                                                latch.countDown();
                                            }
                                        }).start();
                                    })
                            )
                    )
                    .build()
                    .start()
                    .await(TIMEOUT);

            for (int i = 0; i < 5; i++) {
                //Sending request with fake content length, but not continuing after 100 code
                incomplete100Call(webServer);
            }

            // Wait for error from each thread
            // latch result is ignored for better assertion message later
            latch.await(TIMEOUT.getSeconds(), TimeUnit.SECONDS);

            exceptions.forEach(e -> {
                assertThat(e.getCause(), anyOf(
                        // One of the following exceptions is expected:
                        // IOE: Connection reset by peer - on MacOS
                        // ISE: Channel closed prematurely by other side! - on Linux
                        instanceOf(IllegalStateException.class),
                        instanceOf(IOException.class))
                );
            });
            threadFactory.threads.forEach(t -> {
                assertThat("Thread " + t.getName() + " is in invalid state.", t.getState(), is(Thread.State.TERMINATED));
            });
            assertThat(exceptions.size(), is(5));
        } finally {
            if (webServer != null) {
                webServer.shutdown();
            }
        }
    }

    /**
     * Force Netty to avoid auto close and wait for content with fake content-length
     */
    private void incomplete100Call(WebServer webServer) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLocalHost(), webServer.port())) {
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            pw.println("POST / HTTP/1.1");
            pw.println("Host: 127.0.0.1");
            pw.println("Expect: 100-continue");
            pw.println("content-length: 100");
            pw.println("");
            pw.flush();
        }
    }

    private static class TestThreadFactory implements ThreadFactory {
        List<Thread> threads = new ArrayList<>();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            threads.add(t);
            return t;
        }
    }

}

