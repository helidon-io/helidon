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

package io.helidon.webclient.dns.resolver.roundrobin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class ControlledInetAddressResolverProvider extends InetAddressResolverProvider {
    static final String HOSTNAME = "round-robin.test";
    static final String COLD_HOSTNAME = "cold-round-robin.test";

    private static final int TEST_TIMEOUT_SECONDS = 10;
    private static final AtomicReference<LookupBarrier> LOOKUP_BARRIER = new AtomicReference<>();

    static LookupBarrier installLookupBarrier() {
        LookupBarrier barrier = new LookupBarrier();
        if (!LOOKUP_BARRIER.compareAndSet(null, barrier)) {
            throw new IllegalStateException("A lookup barrier is already installed");
        }
        return barrier;
    }

    static void clearLookupBarrier(LookupBarrier barrier) {
        if (!LOOKUP_BARRIER.compareAndSet(barrier, null)) {
            throw new IllegalStateException("The lookup barrier changed unexpectedly");
        }
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new ControlledResolver(configuration.builtinResolver());
    }

    @Override
    public String name() {
        return "controlled-test-resolver";
    }

    private static InetAddress[] addresses(String hostname) throws UnknownHostException {
        return new InetAddress[] {
                InetAddress.getByAddress(hostname, new byte[] {(byte) 192, 0, 2, 1}),
                InetAddress.getByAddress(hostname, new byte[] {(byte) 192, 0, 2, 2}),
                InetAddress.getByAddress(hostname, new byte[] {(byte) 192, 0, 2, 3}),
                InetAddress.getByAddress(hostname, new byte[] {(byte) 192, 0, 2, 4})
        };
    }

    static final class LookupBarrier {
        private final CountDownLatch lookupEntered = new CountDownLatch(1);
        private final CountDownLatch releaseLookup = new CountDownLatch(1);

        private LookupBarrier() {
        }

        boolean awaitLookup() throws InterruptedException {
            return lookupEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        void release() {
            releaseLookup.countDown();
        }

        private void awaitRelease(String hostname) {
            lookupEntered.countDown();
            try {
                if (!releaseLookup.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release DNS lookup for " + hostname);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting to resolve " + hostname, e);
            }
        }
    }

    private static final class ControlledResolver implements InetAddressResolver {
        private final InetAddressResolver builtinResolver;

        private ControlledResolver(InetAddressResolver builtinResolver) {
            this.builtinResolver = builtinResolver;
        }

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
            if (!HOSTNAME.equals(host) && !COLD_HOSTNAME.equals(host)) {
                return builtinResolver.lookupByName(host, lookupPolicy);
            }
            LookupBarrier barrier = LOOKUP_BARRIER.get();
            if (barrier != null && COLD_HOSTNAME.equals(host)) {
                barrier.awaitRelease(host);
            }
            return Stream.of(addresses(host));
        }

        @Override
        public String lookupByAddress(byte[] address) throws UnknownHostException {
            return builtinResolver.lookupByAddress(address);
        }
    }
}
