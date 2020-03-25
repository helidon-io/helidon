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

package io.helidon.config.etcd.client;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;

/**
 * Testing etcd client.
 */
public class MockEtcdClient implements EtcdClient {

    private final static Map<URI, Map<String, String>> content = new ConcurrentHashMap<>();
    private final static Map<URI, Map<String, Long>> revisions = new ConcurrentHashMap<>();
    private final static Map<URI, Map<String, SubmissionPublisher<Long>>> publishers = new ConcurrentHashMap<>();
    private final static ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicLong rev = new AtomicLong();

    private final URI uri;

    public MockEtcdClient(URI uri) {
        this.uri = uri;
        content.computeIfAbsent(uri, (u) -> new ConcurrentHashMap<>());
        revisions.computeIfAbsent(uri, (u) -> new ConcurrentHashMap<>());
        publishers.computeIfAbsent(uri, (u) -> new ConcurrentHashMap<>());
    }

    @Override
    public Long revision(String key) throws EtcdClientException {
        return revisions.get(uri).get(key);
    }

    @Override
    public String get(String key) throws EtcdClientException {
        return content.get(uri).get(key);
    }

    @Override
    public void put(String key, String value) throws EtcdClientException {
        content.get(uri).put(key, value);
        revisions.get(uri).put(key, rev.getAndIncrement());

        SubmissionPublisher<Long> watchPublisher = watchPublisher(key);
        executorService.submit(() -> watchPublisher.submit(0L));
    }

    @Override
    public Flow.Publisher<Long> watch(String key) throws EtcdClientException {
        return publishers.get(uri).computeIfAbsent(key, (k) -> new SubmissionPublisher<>());
    }

    @Override
    public Flow.Publisher<Long> watch(String key, Executor executor) throws EtcdClientException {
        return watch(key);
    }

    public SubmissionPublisher<Long> watchPublisher(String key) {
        return publishers.get(uri).get(key);
    }

    @Override
    public void close() throws EtcdClientException {
    }

}
