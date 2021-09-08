/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.etcd.internal.client.v2;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;

import mousio.client.promises.ResponsePromise;
import mousio.client.promises.ResponsePromise.IsSimplePromiseResponseHandler;
import mousio.client.retry.RetryWithTimeout;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;

/**
 * Etcd API v2 client.
 */
public class EtcdV2Client implements EtcdClient {

    private static final Logger LOGGER = Logger.getLogger(EtcdV2Client.class.getName());

    private final Map<String, SubmissionPublisher<Long>> publishers = new ConcurrentHashMap<>();
    private final mousio.etcd4j.EtcdClient etcd;

    /**
     * Init client with specified target Etcd uri.
     *
     * @param uris target Etcd uris
     */
    EtcdV2Client(URI... uris) {
        etcd = new mousio.etcd4j.EtcdClient(uris);
        etcd.setRetryHandler(new RetryWithTimeout(100, 2000));
    }

    @Override
    public Long revision(String key) throws EtcdClientException {
        try {
            return etcd.get(key).send().get().getNode().modifiedIndex;
        } catch (IOException | TimeoutException | EtcdAuthenticationException e) {
            throw new EtcdClientException("Cannot retrieve modifiedIndex for key " + key);
        } catch (EtcdException e) {
            if (e.errorCode == 100) {
                return null;
            } else {
                throw new EtcdClientException("Cannot retrieve modifiedIndex for key " + key);
            }
        }
    }

    @Override
    public String get(String key) throws EtcdClientException {
        try {
            return etcd.get(key).send().get().getNode().getValue();
        } catch (IOException | TimeoutException | EtcdAuthenticationException e) {
            throw new EtcdClientException("Cannot retrieve key " + key);
        } catch (EtcdException e) {
            if (e.errorCode == 100) {
                return null;
            } else {
                throw new EtcdClientException("Cannot retrieve key " + key);
            }
        }
    }

    @Override
    public void put(String key, String value) throws EtcdClientException {
        try {
            etcd.put(key, value).timeout(1, TimeUnit.SECONDS).send().get();
        } catch (IOException | EtcdException | TimeoutException | EtcdAuthenticationException e) {
            throw new EtcdClientException("Cannot put KV pair under " + key, e);
        }
    }

    @Override
    public Flow.Publisher<Long> watch(String key, Executor executor) throws EtcdClientException {
        final SubmissionPublisher<Long> publisher = publishers.computeIfAbsent(
                key,
                (k) -> new SubmissionPublisher<>(executor,
                                                 Flow.defaultBufferSize()));

        WatchResponseHandler handler = new WatchResponseHandler(publisher, etcd, key);

        handler.waitForChange();

        return publisher;
    }

    @Override
    public Flow.Publisher<Long> watch(String key) throws EtcdClientException {
        return watch(key, Runnable::run //deliver events on current thread
                );
    }

    @Override
    public void close() throws EtcdClientException {
        publishers.values().forEach(SubmissionPublisher::close);
        try {
            etcd.close();
        } catch (IOException ex) {
            throw new EtcdClientException("Error closing gRPC channel, reason: " + ex.getLocalizedMessage(), ex);
        }
    }

    private static class WatchResponseHandler implements IsSimplePromiseResponseHandler<EtcdKeysResponse> {

        private final SubmissionPublisher<Long> publisher;
        private final mousio.etcd4j.EtcdClient etcd;
        private final String key;

        private WatchResponseHandler(SubmissionPublisher<Long> publisher, mousio.etcd4j.EtcdClient etcd, String key) {
            this.publisher = publisher;
            this.etcd = etcd;
            this.key = key;
        }

        @Override
        public void onResponse(ResponsePromise<EtcdKeysResponse> responsePromise) {
            try {
                long modifiedIndex = responsePromise.get().getNode().getModifiedIndex();
                publisher.submit(modifiedIndex);
                waitForChange(modifiedIndex + 1);
            } catch (Exception e) {
                LOGGER.log(Level.CONFIG, "Cannot read changed value.", e);
            }

        }

        private void waitForChange() throws EtcdClientException {
            try {
                etcd.get(key).waitForChange().send().addListener(this);
            } catch (IOException e) {
                throw new EtcdClientException("Cannot register listener on key " + key, e);
            }
        }

        private void waitForChange(long waitIndex) throws EtcdClientException {
            try {
                etcd.get(key).waitForChange(waitIndex).send().addListener(this);
            } catch (IOException e) {
                throw new EtcdClientException("Cannot register listener on key " + key + " and index " + waitIndex + ".", e);
            }
        }
    }

}
