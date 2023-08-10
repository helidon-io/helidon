/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.dns.resolver.first;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.webclient.api.DnsAddressLookup;

import org.junit.jupiter.api.Test;

class FirstDnsResolverTest {

    private static final int THREADS = 100;

    /**
     * Test that {@link io.helidon.webclient.api.DefaultDnsResolver} can be exercised concurrently. A
     * {@link io.helidon.webclient.api.WebClient} used concurrently will use a single resolver.
     *
     * @throws Exception on unexpected condition
     */
    @Test
    void testConcurrency() throws Exception {
        FirstDnsResolver dns = new FirstDnsResolver();
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            Set<Future<?>> futures = new HashSet<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(exec.submit(() -> dns.resolveAddress("localhost", DnsAddressLookup.IPV4)));
            }
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        }
    }
}
