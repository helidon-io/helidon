/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates.
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
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.hocon.HoconConfigParser;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;

/**
 * Integration test for {@link EtcdConfigSource} using {@link EtcdApi#v3}.
 */
public class EtcdConfigSourceIT {

    private static final URI DEFAULT_URI = URI.create("http://localhost:2379");

    @Test
    public void testConfig() throws Exception {
        putConfiguration("/application.conf");
        Config config = Config.builder()
                .sources(EtcdConfigSource.builder()
                                 .uri(DEFAULT_URI)
                                 .key("configuration")
                                 .api(EtcdApi.v3)
                                 .mediaType(MediaTypes.APPLICATION_HOCON)
                                 .build())
                .addParser(HoconConfigParser.create())
                .build();

        assertThat(config.get("security").asNodeList().get(), hasSize(1));
    }

    @Test
    public void testConfigChanges() throws Exception {
        putConfiguration("/application.conf");
        Config config = Config.builder()
                .sources(EtcdConfigSource.builder()
                                 .uri(DEFAULT_URI)
                                 .key("configuration")
                                 .api(EtcdApi.v3)
                                 .mediaType(MediaTypes.APPLICATION_HOCON)
                                 .changeWatcher(EtcdWatcher.create())
                                 .build())
                .addParser(HoconConfigParser.create())
                .build();

        assertThat(config.get("security").asNodeList().get(), hasSize(1));

        CountDownLatch nextLatch = new CountDownLatch(3);
        config.onChange(it -> nextLatch.countDown());

        putConfiguration("/application2.conf");
        TimeUnit.MILLISECONDS.sleep(10);
        putConfiguration("/application3.conf");
        TimeUnit.MILLISECONDS.sleep(10);
        putConfiguration("/application4.conf");

        assertThat(nextLatch.await(20, TimeUnit.SECONDS), is(true));
    }

    private static void putConfiguration(String resourcePath) throws Exception {
        EtcdClient etcd = EtcdApi.v3.clientFactory().createClient(DEFAULT_URI);
        File file = new File(EtcdConfigSourceIT.class.getResource(resourcePath).getFile());
        etcd.put("configuration", String.join("\n",
                Files.readAllLines(file.toPath(), Charset.defaultCharset())));
        etcd.close();
    }
}
