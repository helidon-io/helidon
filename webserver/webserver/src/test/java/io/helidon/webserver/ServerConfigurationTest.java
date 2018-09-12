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

package io.helidon.webserver;

import java.net.InetAddress;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.opentracing.util.GlobalTracer;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ServerConfiguration.Builder}.
 */
public class ServerConfigurationTest {

    @Test
    public void noNegativeValues() throws Exception {
        ServerConfiguration config = ServerConfiguration.builder()
                                                .port(-1)
                                                .backlog(-1)
                                                .receiveBufferSize(-1)
                                                .timeout(-1)
                                                .workersCount(-1)
                                                .build();
        assertEquals(1024, config.backlog());
        assertEquals(0, config.receiveBufferSize());
        assertEquals(0, config.timeoutMillis());
        assertTrue(config.workersCount() > 0);
    }

    @Test
    public void expectedDefaults() throws Exception {
        ServerConfiguration config = ServerConfiguration.builder().build();
        assertEquals(0, config.port());
        assertEquals(1024, config.backlog());
        assertEquals(0, config.receiveBufferSize());
        assertEquals(0, config.timeoutMillis());
        assertTrue(config.workersCount() > 0);
        assertThat(config.tracer(), IsInstanceOf.instanceOf(GlobalTracer.class));
        assertNull(config.bindAddress());
    }

    @Test
    public void respectValues() throws Exception {
        ServerConfiguration config = ServerConfiguration.builder()
                .port(10)
                .backlog(20)
                .receiveBufferSize(30)
                .timeout(40)
                .workersCount(50)
                .bindAddress(InetAddress.getLocalHost())
                .build();
        assertEquals(10, config.port());
        assertEquals(20, config.backlog());
        assertEquals(30, config.receiveBufferSize());
        assertEquals(40, config.timeoutMillis());
        assertEquals(50, config.workersCount());
        assertEquals(InetAddress.getLocalHost(), config.bindAddress());
    }

    @Test
    public void fromConfig() throws Exception {
        Config config = Config.builder().sources(ConfigSources.classpath("config1.conf")).build();
        ServerConfiguration sc = config.get("webserver").as(ServerConfiguration.class);
        assertNotNull(sc);
        assertEquals(10, sc.port());
        assertEquals(20, sc.backlog());
        assertEquals(30, sc.receiveBufferSize());
        assertEquals(40, sc.timeoutMillis());
        assertEquals(InetAddress.getByName("127.0.0.1"), sc.bindAddress());
        assertNull(sc.ssl());

        assertEquals(50, sc.workersCount());

        assertEquals(11, sc.socket("secure").port());
        assertEquals(21, sc.socket("secure").backlog());
        assertEquals(31, sc.socket("secure").receiveBufferSize());
        assertEquals(41, sc.socket("secure").timeoutMillis());
        assertEquals(InetAddress.getByName("127.0.0.2"), sc.socket("secure").bindAddress());
        assertNull(sc.socket("secure").ssl());

        assertEquals(12, sc.socket("other").port());
        assertEquals(22, sc.socket("other").backlog());
        assertEquals(32, sc.socket("other").receiveBufferSize());
        assertEquals(42, sc.socket("other").timeoutMillis());
        assertEquals(InetAddress.getByName("127.0.0.3"), sc.socket("other").bindAddress());
        assertNull(sc.socket("other").ssl());
    }

    @Test
    public void sslFromConfig() throws Exception {
        Config config = Config.builder().sources(ConfigSources.classpath("config-with-ssl.conf")).build();
        ServerConfiguration sc = config.get("webserver").as(ServerConfiguration.class);
        assertNotNull(sc);
        assertEquals(10, sc.port());
        assertNotNull(sc.ssl());

        assertEquals(11, sc.socket("secure").port());
        assertNotNull(sc.socket("secure").ssl());
    }
}
