/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigParser;

/**
 * A config source which loads a configuration document from Etcd.
 * <p>
 * Config source is initialized by {@link EtcdConfigSourceBuilder}.
 *
 * @see EtcdConfigSourceBuilder
 */
public class EtcdConfigSource extends AbstractParsableConfigSource<Long> {

    private static final Logger LOGGER = Logger.getLogger(EtcdConfigSource.class.getName());

    private final EtcdEndpoint endpoint;
    private final EtcdClient client;

    EtcdConfigSource(EtcdConfigSourceBuilder builder) {
        super(builder);

        endpoint = builder.target();
        client = endpoint.api()
                .clientFactory()
                .createClient(endpoint.uri());
    }

    @Override
    protected String mediaType() {
        return Optional.ofNullable(super.mediaType())
                .or(this::probeContentType)
                .orElse(null);
    }

    private Optional<String> probeContentType() {
        return Optional.ofNullable(ConfigHelper.detectContentType(Paths.get(endpoint.key())));
    }

    @Override
    protected String uid() {
        return endpoint.uri() + "#" + endpoint.key();
    }

    @Override
    protected Optional<Long> dataStamp() {
        try {
            return Optional.of(etcdClient().revision(endpoint.key()));
        } catch (EtcdClientException e) {
            return Optional.empty();
        }
    }

    @Override
    protected ConfigParser.Content<Long> content() throws ConfigException {
        String content = null;
        try {
            content = etcdClient().get(endpoint.key());
        } catch (EtcdClientException e) {
            LOGGER.log(Level.FINEST, "Get operation threw an exception.", e);
        }

        // a KV pair does not exist
        if (content == null) {
            throw new ConfigException(String.format("Key '%s' does not contain any value", endpoint.key()));
        }
        return ConfigParser.Content.create(new StringReader(content), mediaType(), dataStamp());
    }

    EtcdEndpoint etcdEndpoint() {
        return endpoint;
    }

    EtcdClient etcdClient() {
        return client;
    }

    /**
     * Create a configured instance with the provided options.
     *
     * @param uri Remote etcd URI
     * @param key key the configuration is associated with
     * @param api api version
     * @return a new etcd config source
     */
    public static EtcdConfigSource create(URI uri, String key, EtcdConfigSourceBuilder.EtcdApi api) {
        return builder()
                .uri(uri)
                .key(key)
                .api(api)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param metaConfig meta configuration to load config source from
     * @return configured source instance
     */
    public static EtcdConfigSource create(Config metaConfig) {
        return builder()
                .config(metaConfig)
                .build();
    }

    /**
     * Create a new fluent API builder for etcd.
     *
     * @return a new builder
     */
    public static EtcdConfigSourceBuilder builder() {
        return new EtcdConfigSourceBuilder();
    }
}
