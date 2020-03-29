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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.client.MockEtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.spi.ConfigParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
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
        EtcdConfigSource etcdConfigSource = EtcdConfigSource.builder()
                .key("key")
                .api(EtcdApi.v2)
                .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                .build();

        assertThat(etcdConfigSource, notNullValue());
    }

    @Test
    public void testBadUri() {
        assertThrows(ConfigException.class, () -> {
            EtcdConfigSource etcdConfigSource = EtcdConfigSource.builder()
                    .uri(URI.create("http://localhost:1111"))
                    .key("configuration")
                    .api(EtcdApi.v2)
                    .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                    .build();

            etcdConfigSource.load();
        });
    }

    @Test
    public void testBadKey() {
        assertThrows(ConfigException.class, () -> {
            EtcdConfigSource etcdConfigSource = EtcdConfigSource.builder()
                    .uri(DEFAULT_URI)
                    .key("non-existing-key-23323423424234")
                    .api(EtcdApi.v2)
                    .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                    .build();

            etcdConfigSource.load();
        });
    }

    @Test
    public void testConfig() {
        final AtomicLong revision = new AtomicLong(0);

        EtcdConfigSource configSource = EtcdConfigSource.builder()
                .uri(DEFAULT_URI)
                .key("configuration")
                .api(EtcdApi.v2)
                .mediaType(MEDIA_TYPE_APPLICATION_HOCON)
                .build();

        EtcdConfigSource mockedConfigSource = spy(configSource);
        when(mockedConfigSource.etcdClient()).thenReturn(etcdClient);
        when(mockedConfigSource.load()).thenReturn(Optional.of(new ConfigParser.Content() {
            @Override
            public Optional<String> mediaType() {
                return Optional.of(MEDIA_TYPE_APPLICATION_HOCON);
            }

            @Override
            public InputStream data() {
                try {
                    return new ByteArrayInputStream(etcdClient.get("configuration").getBytes(StandardCharsets.UTF_8));
                } catch (EtcdClientException e) {
                    fail(e);
                    return null;
                }
            }

            @Override
            public Charset charset() {
                return StandardCharsets.UTF_8;
            }

            @Override
            public Optional<Object> stamp() {
                return Optional.of(revision.getAndIncrement());
            }
        }));

        Config config = Config.builder()
                .sources(mockedConfigSource)
                .build();

        assertThat(config.get("security").asNodeList().get(), hasSize(1));
    }

    private void putConfiguration(String resourcePath) throws Exception {
        File file = new File(EtcdConfigSourceTest.class.getResource(resourcePath).toURI());
        etcdClient.put("configuration",
                       String.join("\n", Files.readAllLines(file.toPath(), Charset.defaultCharset())));
    }
}
