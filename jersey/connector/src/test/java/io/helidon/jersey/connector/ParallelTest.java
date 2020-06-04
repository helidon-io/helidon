/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


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
        final UncachedStringMethodExecutor executor = new UncachedStringMethodExecutor(resource::get);

        AbstractTest.extensions.set(new Extension[] {
                executor,
                new ContentLengthSetter()
        });

        AbstractTest.rules.set(
                () -> {
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo(PATH)).willReturn(
                                    WireMock.ok().withTransformers(executor.getName())
                            )
                    );
                });

        AbstractTest.setup();
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
                            Response response;
                            response = target.path(PATH).request().get();
                            Assertions.assertEquals("GET", response.readEntity(String.class));
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

            Assertions.assertTrue(
                    doneLatch.await(10, TimeUnit.SECONDS),
                    "Waiting for clients to finish has timed out."
            );

            Assertions.assertEquals(PARALLEL_CLIENTS, resourceCounter.get(), "Resource counter");

            Assertions.assertEquals(PARALLEL_CLIENTS, receivedCounter.get(), "Received counter");
        } finally {
            executor.shutdownNow();
            Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor termination");
        }
    }
}
