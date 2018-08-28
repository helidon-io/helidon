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

package io.helidon.config.etcd;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.client.MockEtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.spi.ConfigParser;

import com.google.common.io.Files;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests {@link EtcdConfigSource} with {@link MockEtcdClient}.
 */
public class EtcdConfigSourceTest {

    static final String MEDIA_TYPE_APPLICATION_HOCON = "application/hocon";

    private static final URI DEFAULT_URI = URI.create("http://localhost:2379");

    private EtcdClient etcdClient;

    @BeforeEach
    public void before() throws Exception {
        etcdClient = new MockEtcdClient(DEFAULT_URI);
        putConfiguration("/application.conf");
    }

    @Test
    public void testConfigSourceBuilder() {
        EtcdConfigSource etcdConfigSource = (EtcdConfigSource) EtcdConfigSourceBuilder
                .from(DEFAULT_URI, "key", EtcdApi.v2)
                .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                .build();

        assertThat(etcdConfigSource, notNullValue());
    }

    @Test
    public void testBadUri() {
        Assertions.assertThrows(ConfigException.class, () -> {
        EtcdConfigSource etcdConfigSource = (EtcdConfigSource) EtcdConfigSourceBuilder
                .from(URI.create("http://localhost:1111"), "configuration", EtcdApi.v2)
                .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                .build();

        etcdConfigSource.content();
        });
    }

    @Test
    public void testBadKey() {
        Assertions.assertThrows(ConfigException.class, () -> {
        EtcdConfigSource etcdConfigSource = (EtcdConfigSource) EtcdConfigSourceBuilder
                .from(DEFAULT_URI, "non-existing-key-23323423424234", EtcdApi.v2)
                .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                .build();

        etcdConfigSource.content();
        });
    }

    @Test
    public void testConfig() {
        final AtomicLong revision = new AtomicLong(0);

        EtcdConfigSource configSource = (EtcdConfigSource) EtcdConfigSourceBuilder
                .from(DEFAULT_URI, "configuration", EtcdApi.v2)
                .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                .build();

        EtcdConfigSource mockedConfigSource = spy(configSource);
        when(mockedConfigSource.getEtcdClient()).thenReturn(etcdClient);
        when(mockedConfigSource.content()).thenReturn(new ConfigParser.Content<Long>() {
            @Override
            public String getMediaType() {
                return MEDIA_TYPE_APPLICATION_HOCON;
            }

            @Override
            public Readable asReadable() {
                try {
                    return new StringReader(etcdClient.get("configuration"));
                } catch (EtcdClientException e) {
                    Assertions.fail(e);
                    return null;
                }
            }

            @Override
            public Optional<Long> getStamp() {
                return Optional.of(revision.getAndIncrement());
            }
        });

        Config config = Config.builder()
                .sources(mockedConfigSource)
                .build();

        assertThat(config.get("security").asNodeList().size(), is(1));
    }

    private void putConfiguration(String resourcePath) throws Exception {
        File file = new File(EtcdConfigSourceTest.class.getResource(resourcePath).getFile());
        etcdClient.put("configuration",
                       Files.readLines(file, Charset.defaultCharset()).stream().collect(Collectors.joining("\n")));
    }
}
