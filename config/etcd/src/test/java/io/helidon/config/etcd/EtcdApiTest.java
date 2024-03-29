/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.internal.client.v2.EtcdV2Client;
import io.helidon.config.etcd.internal.client.v3.EtcdV3Client;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link EtcdApi}.
 */
public class EtcdApiTest {

    @Test
    public void testClientVersion() {
        assertThat(EtcdApi.v2.clientFactory().createClient(URI.create("http://localhost")), instanceOf(EtcdV2Client.class));
        assertThat(EtcdApi.v3.clientFactory().createClient(URI.create("http://localhost")), instanceOf(EtcdV3Client.class));
    }

    @Test
    public void testMultipleUrisV2() {
        assertThat(EtcdApi.v2.clientFactory().createClient(
                        URI.create("http://localhost"), URI.create("http://localhost")),
                instanceOf(EtcdV2Client.class));
    }

    @Test
    public void testMultipleUrisV3() {
        assertThrows(IllegalArgumentException.class,
                () -> EtcdApi.v3.clientFactory().createClient(
                        URI.create("http://localhost"), URI.create("http://localhost")));
    }
}
