/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.etcd.client;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.etcd.internal.client.v2.EtcdV2Client;
import io.helidon.config.etcd.internal.client.v3.EtcdV3Client;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import org.hamcrest.core.Is;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link EtcdClient}s that expect a running etcd at default {@code http://localhost:2379}.
 */
public class EtcdClientIT {

    private static final URI uri = URI.create("http://localhost:2379");

    private static Stream<Class<? extends EtcdClient>> getClients() {
        return CollectionsHelper.listOf(EtcdV2Client.class, EtcdV3Client.class).stream();
    }

    @ParameterizedTest
    @MethodSource("getClients")
    public <T extends EtcdClient> void testPutGet(Class<T> clientClass) throws EtcdClientException {
        runTest(clientClass, etcdClient -> {
            etcdClient.put("key", "value");
            String result = etcdClient.get("key");
            assertThat(result, is("value"));
            
        });
    }
    
    @ParameterizedTest
    @MethodSource("getClients")
    public <T extends EtcdClient> void testGetNonExistingKey(Class<T> clientClass) throws EtcdClientException {
        runTest(clientClass, etcdClient -> {
            String result = etcdClient.get("non-existing-key");
            assertThat(result, nullValue());
        });
    }

    @ParameterizedTest
    @MethodSource("getClients")
    public <T extends EtcdClient> void testWatchNewKey(Class<T> clientClass) throws EtcdClientException, ExecutionException, InterruptedException {
        runTest(clientClass, etcdClient -> {
            final String key = "key#" + new Random().nextLong();
            final String finalValue = "new value";

            etcdClient.put(key, "any value to change (just to be sure there is not already set to the final value");

            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch nextLatch = new CountDownLatch(1);

            Flow.Subscriber<Long> subscriber = new Flow.Subscriber<Long>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                    initLatch.countDown();
                }

                @Override
                public void onNext(Long item) {
                    nextLatch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                }

                @Override
                public void onComplete() {

                }
            };
            etcdClient.watch(key).subscribe(subscriber);

            assertThat(initLatch.await(1, TimeUnit.SECONDS), Is.is(true));

            etcdClient.put(key, finalValue);

            assertThat(nextLatch.await(20, TimeUnit.SECONDS), is(true));
        });
    }

    @ParameterizedTest
    @MethodSource("getClients")
    public <T extends EtcdClient> void testWatchValueChanges(Class<T> clientClass) throws EtcdClientException, ExecutionException, InterruptedException {
        runTest(clientClass, etcdClient -> {
            final String key = "key";

            etcdClient.put(key, "any value to change (just to be sure there is not already set to the final value");

            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch nextLatch = new CountDownLatch(3);

            Flow.Subscriber<Long> subscriber = new Flow.Subscriber<Long>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                    initLatch.countDown();
                }

                @Override
                public void onNext(Long item) {
                    nextLatch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                }

                @Override
                public void onComplete() {
                }
            };
            etcdClient.watch(key).subscribe(subscriber);

            assertThat(initLatch.await(1, TimeUnit.SECONDS), is(true));

            etcdClient.put(key, "value #2");
            etcdClient.put(key, "value #3");
            etcdClient.put(key, "value #4");

            assertThat(nextLatch.await(20, TimeUnit.SECONDS), is(true));
        });
    }
    
    /**
     * Customized Consumer interface with the accept method throwing exceptions
     * that are thrown by the lambda expressions used by the tests (and therefore
     * invoked by the accept method itself).
     */
    @FunctionalInterface
    private interface EtcdClientConsumer {
        public void accept(EtcdClient t) throws EtcdClientException, InterruptedException;
    }
    
    private <T extends EtcdClient> void runTest(
            Class<T> clientClass,
            EtcdClientConsumer test) throws EtcdClientException {
        try {
            try (EtcdClient etcdClient = clientClass.getDeclaredConstructor(URI.class).newInstance(uri)) {
                test.accept(etcdClient);
            } catch (EtcdClientException ex) {
                Assertions.fail(ex);
            }
        } catch (NoSuchMethodException 
                | InstantiationException 
                | IllegalAccessException 
                | InvocationTargetException
                | InterruptedException ex) {
            Assertions.fail(ex);
        }
    }
}

