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

package io.helidon.security.providers.common;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.configurable.ResourceConfig;
import io.helidon.common.configurable.ResourceException;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.faulttolerance.RetryConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResilientResourceTest {
    @Test
    void stalledUriReadIsBoundedAndOpensCircuit() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            Thread serverThread = Thread.ofVirtual().start(() -> {
                try (Socket ignored = serverSocket.accept()) {
                    accepted.countDown();
                    releaseServer.await();
                } catch (Throwable t) {
                    serverFailure.set(t);
                }
            });

            try {
                URI uri = URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/jwks");
                ResourceConfig resourceConfig = ResourceConfig.builder()
                        .uri(uri)
                        .buildPrototype();
                Duration ioTimeout = Duration.ofMillis(50);
                AtomicInteger loads = new AtomicInteger();
                ResilientValue<byte[]> value = ResilientValue.create(
                        "stalled URI resource",
                        () -> {
                            loads.incrementAndGet();
                            try {
                                return ResilientResource.create("stalled URI resource", resourceConfig, ioTimeout)
                                        .bytes();
                            } catch (ResourceException e) {
                                throw new ResilientValue.UnavailableException("stalled URI resource could not be read", e);
                            }
                        },
                        RetryConfig.builder()
                                .calls(1)
                                .delay(Duration.ZERO)
                                .overallTimeout(ioTimeout)
                                .addApplyOn(ResilientValue.UnavailableException.class)
                                .build(),
                        CircuitBreakerConfig.builder()
                                .volume(1)
                                .errorRatio(100)
                                .successThreshold(1)
                                .delay(Duration.ofMinutes(1))
                                .addApplyOn(ResilientValue.UnavailableException.class)
                                .build());

                long beforeFirstLoad = System.nanoTime();
                assertThrows(ResilientValue.UnavailableException.class, value::get);
                long firstLoadMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beforeFirstLoad);

                assertThat("test server accepted the load", accepted.await(5, TimeUnit.SECONDS), is(true));
                assertThat("stalled load respected its I/O timeout", firstLoadMillis < 5_000, is(true));
                assertThrows(ResilientValue.UnavailableException.class, value::get);
                assertThat("open circuit suppressed another connection", loads.get(), is(1));
            } finally {
                releaseServer.countDown();
                serverThread.join(TimeUnit.SECONDS.toMillis(5));
                assertThat("test server stopped", serverThread.isAlive(), is(false));
                assertThat("test server failure", serverFailure.get(), is((Throwable) null));
            }
        }
    }
}
