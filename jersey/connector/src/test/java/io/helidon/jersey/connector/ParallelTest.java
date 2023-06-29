/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Extension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests the parallel execution of multiple requests.
 */
public class ParallelTest extends AbstractTest {
    private static final Logger LOGGER = Logger.getLogger(ParallelTest.class.getName());

    private static final int PARALLEL_CLIENTS = 10;
    private static final String PATH = "/test";
    private static final AtomicInteger receivedCounter = new AtomicInteger(0);
    private static final AtomicInteger resourceCounter = new AtomicInteger(0);
    private static final CyclicBarrier startBarrier = new CyclicBarrier(PARALLEL_CLIENTS + 1);
    private static final CountDownLatch doneLatch = new CountDownLatch(PARALLEL_CLIENTS);
    private static final MyResource resource = new MyResource();

    @Path(PATH)
    public static class MyResource {

        @GET
        public String get() {
            sleep();
            resourceCounter.addAndGet(1);
            return "GET";
        }

        private void sleep() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    @BeforeAll
    public static void setup() {
        LOGGER.addHandler(new ConsoleHandler());
        UncachedStringMethodExecutor executor = new UncachedStringMethodExecutor(resource::get);

        Extension[] extensions = new Extension[]{
                executor,
                new ContentLengthSetter()
        };
        Rules rules = () -> wireMockServer.stubFor(
                WireMock.get(WireMock.urlEqualTo(PATH)).willReturn(
                        WireMock.ok().withTransformers(executor.getName())
                )
        );
        setup(rules, extensions);
    }

    @Test
    public void testParallel() throws BrokenBarrierException, InterruptedException, TimeoutException {
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(PARALLEL_CLIENTS);

        try {
            final WebTarget target = target("");
            for (int i = 1; i <= PARALLEL_CLIENTS; i++) {
                final int id = i;
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startBarrier.await();
                            Response response = target.path(PATH).request().get();
                            assertThat(response.readEntity(String.class), is("GET"));
                            receivedCounter.incrementAndGet();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            LOGGER.log(Level.WARNING, "Client thread " + id + " interrupted.", ex);
                        } catch (BrokenBarrierException ex) {
                            LOGGER.log(Level.INFO, "Client thread " + id + " failed on broken barrier.", ex);
                        } catch (Throwable t) {
                            t.printStackTrace();
                            LOGGER.log(Level.WARNING, "Client thread " + id + " failed on unexpected exception.", t);
                        } finally {
                            doneLatch.countDown();
                        }
                    }
                });
            }

            startBarrier.await(1, TimeUnit.SECONDS);

            assertThat("Waiting for clients to finish has timed out.",
                    doneLatch.await(10, TimeUnit.SECONDS),
                    is(true)
            );
            assertThat("Resource counter", resourceCounter.get(), is(PARALLEL_CLIENTS));
            assertThat("Received counter", receivedCounter.get(), is(PARALLEL_CLIENTS));
        } finally {
            executor.shutdownNow();
            assertThat("Executor termination", executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        }
    }
}
