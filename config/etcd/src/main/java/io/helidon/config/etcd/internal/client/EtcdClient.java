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

package io.helidon.config.etcd.internal.client;

import java.util.concurrent.Executor;

import io.helidon.common.reactive.Flow;

/**
 * Etcd client interface provides basic get operation.
 */
public interface EtcdClient extends AutoCloseable {

    /**
     * Gets a modification revision.
     *
     * @param key the key
     * @return a revision
     * @throws EtcdClientException in case the action fails
     */
    Long revision(String key) throws EtcdClientException;

    /**
     * Get the value of a key.
     *
     * @param key the key
     * @return the corresponding value or null if the key does not exist
     * @throws EtcdClientException  in case the action fails
     * @throws NullPointerException in case of {@code key} is {@code null}
     */
    String get(String key) throws EtcdClientException, NullPointerException;

    /**
     * Associates specified value with given key.
     *
     * @param key   a key the value should be associate with
     * @param value a value
     * @throws EtcdClientException  in case the action fails
     * @throws NullPointerException in case of {@code key} is {@code null}
     */
    void put(String key, String value) throws EtcdClientException, NullPointerException;

    /**
     * Watch for a change on a key, using a default executor on the caller's
     * thread to deliver events.
     *
     * @param key the key
     * @return a result publisher
     * @throws EtcdClientException in case the action fails
     * @throws NullPointerException in case of {@code key} is {@code null}
     */
    Flow.Publisher<Long> watch(String key) throws EtcdClientException, NullPointerException;

    /**
     * Watch for a change on a key, using the specified executor to deliver events.
     *
     * @param key the key
     * @param executor the {@link Executor} to use for delivering events
     * @return a result publisher
     * @throws EtcdClientException  in case the action fails
     * @throws NullPointerException in case of {@code key} is {@code null}
     */
    Flow.Publisher<Long> watch(String key, Executor executor) throws EtcdClientException, NullPointerException;

    @Override
    void close() throws EtcdClientException;
}
