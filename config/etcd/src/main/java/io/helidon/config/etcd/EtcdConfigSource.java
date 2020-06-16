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
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.AbstractConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

/**
 * A config source which loads a configuration document from Etcd.
 * <p>
 * Config source is initialized by {@link EtcdConfigSourceBuilder}.
 *
 * @see EtcdConfigSourceBuilder
 */
public class EtcdConfigSource extends AbstractConfigSource
        implements PollableSource<Long>, WatchableSource<EtcdEndpoint>, ParsableSource {

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
    protected String uid() {
        return endpoint.uri() + "#" + endpoint.key();
    }

    @Override
    public Optional<ConfigParser> parser() {
        return super.parser();
    }

    @Override
    public Optional<String> mediaType() {
        return super.mediaType();
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    @Override
    public Optional<ChangeWatcher<Object>> changeWatcher() {
        return super.changeWatcher();
    }

    @Override
    public EtcdEndpoint target() {
        return endpoint;
    }

    @Override
    public Class<EtcdEndpoint> targetType() {
        return EtcdEndpoint.class;
    }

    @Override
    public boolean isModified(Long stamp) {
        return dataStamp()
                .map(newStamp -> (newStamp > stamp))
                .orElse(false);
    }

    @Override
    public Optional<Content> load() throws ConfigException {
        String content;
        try {
            content = etcdClient().get(endpoint.key());
        } catch (EtcdClientException e) {
            LOGGER.log(Level.FINEST, "Get operation threw an exception.", e);
            throw new ConfigException(String.format("Could not get data for key '%s'", endpoint.key()), e);
        }

        // a KV pair does not exist
        if (content == null) {
            return Optional.empty();
        }

        Content.Builder builder = Content.builder()
                .data(toInputStream(content))
                .charset(StandardCharsets.UTF_8);

        MediaTypes.detectType(endpoint.key()).ifPresent(builder::mediaType);
        dataStamp().ifPresent(builder::stamp);

        return Optional.of(builder.build());
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<Long> dataStamp() {
        try {
            return Optional.of(etcdClient().revision(endpoint.key()));
        } catch (EtcdClientException e) {
            return Optional.empty();
        }
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
