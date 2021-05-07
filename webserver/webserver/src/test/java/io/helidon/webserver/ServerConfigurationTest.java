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

package io.helidon.webserver;

import java.net.InetAddress;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.opentracing.util.GlobalTracer;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

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
        assertThat(config.backlog(), is(1024));
        assertThat(config.receiveBufferSize(), is(0));
        assertThat(config.timeoutMillis(), is(0));
        assertThat(config.workersCount() > 0, is(true));
    }

    @Test
    public void expectedDefaults() throws Exception {
        ServerConfiguration config = ServerConfiguration.builder().build();
        assertThat(config.port(), is(0));
        assertThat(config.backlog(), is(1024));
        assertThat(config.receiveBufferSize(), is(0));
        assertThat(config.timeoutMillis(), is(0));
        assertThat(config.workersCount() > 0, is(true));
        assertThat(config.tracer(), IsInstanceOf.instanceOf(GlobalTracer.class));
        assertThat(config.bindAddress(), nullValue());
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
        assertThat(config.port(), is(10));
        assertThat(config.backlog(), is(20));
        assertThat(config.receiveBufferSize(), is(30));
        assertThat(config.timeoutMillis(), is(40));
        assertThat(config.workersCount(), is(50));
        assertThat(config.bindAddress(), is(InetAddress.getLocalHost()));
    }

    @Test
    public void fromConfig() throws Exception {
        Config config = Config.builder().sources(ConfigSources.classpath("config1.conf")).build();
        ServerConfiguration sc = config.get("webserver").as(ServerConfiguration::create).get();
        assertThat(sc, notNullValue());
        assertThat(sc.port(), is(10));
        assertThat(sc.backlog(), is(20));
        assertThat(sc.receiveBufferSize(), is(30));
        assertThat(sc.timeoutMillis(), is(40));
        assertThat(sc.bindAddress(), is(InetAddress.getByName("127.0.0.1")));
        assertThat(sc.enabledSslProtocols(), hasSize(0));
        assertThat(sc.ssl(), nullValue());

        assertThat(sc.workersCount(), is(50));

        assertThat(sc.socket("secure").port(), is(11));
        assertThat(sc.socket("secure").backlog(), is(21));
        assertThat(sc.socket("secure").receiveBufferSize(), is(31));
        assertThat(sc.socket("secure").timeoutMillis(), is(41));
        assertThat(sc.socket("secure").bindAddress(), is(InetAddress.getByName("127.0.0.2")));
        assertThat(sc.socket("secure").enabledSslProtocols(), hasSize(0));
        assertThat(sc.socket("secure").ssl(), nullValue());

        assertThat(sc.socket("other").port(), is(12));
        assertThat(sc.socket("other").backlog(), is(22));
        assertThat(sc.socket("other").receiveBufferSize(), is(32));
        assertThat(sc.socket("other").timeoutMillis(), is(42));
        assertThat(sc.socket("other").bindAddress(), is(InetAddress.getByName("127.0.0.3")));
        assertThat(sc.socket("other").enabledSslProtocols(), hasSize(0));
        assertThat(sc.socket("other").ssl(), nullValue());
    }

    @Test
    public void sslFromConfig() throws Exception {
        Config config = Config.builder().sources(ConfigSources.classpath("config-with-ssl.conf")).build();
        ServerConfiguration sc = config.get("webserver").as(ServerConfiguration::create).get();
        assertThat(sc, notNullValue());
        assertThat(sc.port(), is(10));
        assertThat(sc.ssl(), notNullValue());

        assertThat(sc.socket("secure").port(), is(11));
        assertThat(sc.socket("secure").enabledSslProtocols(), contains("TLSv1.2"));
        assertThat(sc.socket("secure").ssl(), notNullValue());
    }
}
