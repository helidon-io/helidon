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

import io.helidon.common.reactive.Flow;
import java.net.URI;

import io.helidon.config.ConfigException;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.internal.client.v2.EtcdV2Client;
import io.helidon.config.etcd.internal.client.v3.EtcdV3Client;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link EtcdUtils}.
 */
public class EtcdUtilsTest {

    @Test
    public void getClientClass() {
        assertThat(EtcdUtils.getClientClass(EtcdApi.v2), is(equalTo(EtcdV2Client.class)));
        assertThat(EtcdUtils.getClientClass(EtcdApi.v3), is(equalTo(EtcdV3Client.class)));
    }

    @Test
    public void getClient() {
        assertThat(EtcdUtils.getClient(EtcdV2Client.class, URI.create("http://localhost")), instanceOf(EtcdV2Client.class));
    }

    @Test
    public void getClientCannotInstantiate() {
        ConfigException ce = assertThrows(ConfigException.class, () -> {
        
        EtcdUtils.getClient(ClientWithoutRequiredConstructor.class, URI.create("http://localhost"));
        });
        
        assertTrue(ce.getMessage().startsWith("Cannot instantiate etcd client class " + ClientWithoutRequiredConstructor.class.getName() + "."));
    }

    private class ClientWithoutRequiredConstructor implements EtcdClient {

        public ClientWithoutRequiredConstructor() {
        }

        @Override
        public Long revision(String key) throws EtcdClientException {
            return null;
        }

        @Override
        public String get(String key) throws EtcdClientException {
            return null;
        }

        @Override
        public void put(String key, String value) throws EtcdClientException {

        }

        @Override
        public Flow.Publisher<Long> watch(String key) throws EtcdClientException {
            return null;
        }

        @Override
        public Flow.Publisher<Long> watch(String key, Executor executor) throws EtcdClientException {
            return null;
        }

        @Override
        public void close() throws EtcdClientException {
        }
    }
}
