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

import java.net.URI;

import io.helidon.common.Builder;
import io.helidon.config.AbstractConfigSourceBuilder;
import io.helidon.config.Config;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.etcd.internal.client.EtcdClientFactory;
import io.helidon.config.etcd.internal.client.v2.EtcdV2ClientFactory;
import io.helidon.config.etcd.internal.client.v3.EtcdV3ClientFactory;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

/**
 * Etcd ConfigSource builder.
 * <p>
 * It allows to configure following properties:
 * <ul>
 * <li>{@code uri} - etcd endpoint</li>
 * <li>{@code key} - an etcd key that is associated to value with configuration</li>
 * <li>{@code version} - an etcd API version</li>
 * <li>{@code mandatory} - is existence of configuration resource mandatory (by default) or is {@code optional}?</li>
 * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
 * <li>{@code parser} - or directly set {@link ConfigParser} instance to be used to parse the source;</li>
 * </ul>
 * <p>
 * One of {@code media-type} and {@code parser} properties must be set to be clear how to parse the content. If both of them
 * are set, then {@code parser} has precedence.
 */
public final class EtcdConfigSourceBuilder extends AbstractConfigSourceBuilder<EtcdConfigSourceBuilder, EtcdEndpoint>
        implements PollableSource.Builder<EtcdConfigSourceBuilder>,
                   WatchableSource.Builder<EtcdConfigSourceBuilder, EtcdEndpoint>,
                   ParsableSource.Builder<EtcdConfigSourceBuilder>,
                   Builder<EtcdConfigSource> {

    /**
     * Default Etcd API version ({@link io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi#v2}).
     */
    public static final EtcdApi DEFAULT_VERSION = EtcdApi.v2;
    /**
     * Default Etcd endpoint ({@code http://localhost:2379}).
     */
    public static final URI DEFAULT_URI = URI.create("http://localhost:2379");

    private static final String URI_KEY = "uri";
    private static final String KEY_KEY = "key";
    private static final String API_KEY = "api";

    private EtcdEndpoint etcdEndpoint;

    private URI uri = DEFAULT_URI;
    private String key;
    private EtcdApi version = DEFAULT_VERSION;

    EtcdConfigSourceBuilder() {
    }

    /**
     * Builds new instance of Etcd ConfigSource.
     *
     * @return new instance of Etcd ConfigSource.
     */
    @Override
    public EtcdConfigSource build() {
        // ensure endpoint is configured
        target();

        return new EtcdConfigSource(this);
    }

    /**
     * {@inheritDoc}
     * <ul>
     * <li>{@code uri} - type {@link URI} - Etcd instance remote URI</li>
     * <li>{@code key} - type {@code String} - Etcd key the configuration is associated with</li>
     * <li>{@code api} - type {@link EtcdApi} - Etcd API version such as {@code v3}</li>
     * </ul>
     * Optional {@code properties}: see {@link #config(Config)}.
     *
     * @param metaConfig meta-configuration used to update the builder instance from
     * @return updated builder instance
     * @see #config(Config)
     */
    @Override
    public EtcdConfigSourceBuilder config(Config metaConfig) {
        metaConfig.get(URI_KEY).as(URI.class).ifPresent(this::uri);
        metaConfig.get(KEY_KEY).asString().ifPresent(this::key);
        metaConfig.get(API_KEY).asString().as(EtcdApi::valueOf).ifPresent(this::api);

        return super.config(metaConfig);
    }

    @Override
    public EtcdConfigSourceBuilder parser(ConfigParser parser) {
        return super.parser(parser);
    }

    @Override
    public EtcdConfigSourceBuilder mediaType(String mediaType) {
        return super.mediaType(mediaType);
    }

    @Override
    public EtcdConfigSourceBuilder pollingStrategy(PollingStrategy pollingStrategy) {
        return super.pollingStrategy(pollingStrategy);
    }

    @Override
    public EtcdConfigSourceBuilder changeWatcher(ChangeWatcher<EtcdEndpoint> changeWatcher) {
        return super.changeWatcher(changeWatcher);
    }

    /**
     * Etcd endpoint remote URI.
     *
     * @param uri endpoint URI
     * @return updated builder instance
     */
    public EtcdConfigSourceBuilder uri(URI uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Etcd key with which the value containing the configuration is associated.
     *
     * @param key key
     * @return updated builder instance
     */
    public EtcdConfigSourceBuilder key(String key) {
        this.key = key;
        return this;
    }

    /**
     * Etcd API version.
     *
     * @param version version, defaults to {@link EtcdApi#v3}
     * @return updated builder instance
     */
    public EtcdConfigSourceBuilder api(EtcdApi version) {
        this.version = version;
        return this;
    }

    EtcdEndpoint target() {
        if (null == etcdEndpoint) {
            if (null == uri) {
                throw new IllegalArgumentException("etcd URI must be defined");
            }
            if (null == key) {
                throw new IllegalArgumentException("etcd key must be defined");
            }
            if (null == version) {
                throw new IllegalArgumentException("etcd api (version) must be defined");
            }
            this.etcdEndpoint = new EtcdEndpoint(uri, key, version);
        }
        return etcdEndpoint;
    }

    /**
     * {@code EtcdApi} determines which etcd API version will be used.
     * <p>
     * There are two API versions: {@code v2} and {@code v3}.
     */
    public enum EtcdApi {

        /**
         * Etcd API v2 version.
         */
        v2(new EtcdV2ClientFactory()),

        /**
         * Etcd API v3 version.
         */
        v3(new EtcdV3ClientFactory());

        private final EtcdClientFactory clientFactory;

        EtcdApi(EtcdClientFactory clientFactory) {
            this.clientFactory = clientFactory;
        }

        /**
         * The client factory for this version of etcd.
         * @return client factory
         */
        EtcdClientFactory clientFactory() {
            return clientFactory;
        }
    }

    /**
     * Etcd endpoint remote descriptor.
     * <p>
     * Holds attributes necessary to connect to remote Etcd service.
     */
    public static class EtcdEndpoint {

        private final URI uri;
        private final String key;
        private final EtcdApi api;

        /**
         * Initializes descriptor.
         *
         * @param uri an Etcd endpoint remote URI.
         * @param key an Etcd key with which the value containing the configuration is associated.
         * @param api an Etcd API version.
         */
        public EtcdEndpoint(URI uri, String key, EtcdApi api) {
            this.uri = uri;
            this.key = key;
            this.api = api;
        }

        /**
         * Etcd endpoint remote URI.
         *
         * @return endpoint URI
         */
        public URI uri() {
            return uri;
        }

        /**
         * Etcd key.
         *
         * @return key with configuration
         */
        public String key() {
            return key;
        }

        /**
         * Etcd API version.
         *
         * @return API version
         */
        public EtcdApi api() {
            return api;
        }
    }
}
