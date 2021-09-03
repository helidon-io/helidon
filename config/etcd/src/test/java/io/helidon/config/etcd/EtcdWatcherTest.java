/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.etcd;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.etcd.client.MockEtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link EtcdWatcher}.
 */
public class EtcdWatcherTest {

    private static final URI DEFAULT_URI = URI.create("http://localhost:2379");

    @Test
    public void testBasics() throws EtcdClientException, InterruptedException {
        MockEtcdClient etcdClient = new MockEtcdClient(DEFAULT_URI);
        EtcdWatcher etcdWatcher = new MockEtcdWatcher(etcdClient);

        CountDownLatch nextLatch = new CountDownLatch(3);

        etcdWatcher.start(new EtcdEndpoint(DEFAULT_URI, "key", EtcdApi.v2), change -> nextLatch.countDown());

        etcdClient.put("key", "value1");
        etcdClient.put("key", "value2");
        etcdClient.put("key", "value3");

        assertThat(nextLatch.await(1000, TimeUnit.MILLISECONDS), is(true));

        etcdWatcher.stop();
    }

    @Test
    public void testCannotStartMultiple() {
        MockEtcdClient etcdClient = new MockEtcdClient(DEFAULT_URI);
        EtcdWatcher etcdWatcher = new MockEtcdWatcher(etcdClient);

        etcdWatcher.start(new EtcdEndpoint(DEFAULT_URI, "key", EtcdApi.v2), change -> {
        });

        assertThrows(IllegalStateException.class,
                     () -> etcdWatcher.start(new EtcdEndpoint(DEFAULT_URI, "key", EtcdApi.v2), change -> {
                     }));

        etcdWatcher.stop();
    }

    private static class MockEtcdWatcher extends EtcdWatcher {

        private final MockEtcdClient etcdClient;

        MockEtcdWatcher(MockEtcdClient etcdClient) {
            this.etcdClient = etcdClient;
        }

        @Override
        EtcdClient etcdClient() {
            return etcdClient;
        }
    }
}
