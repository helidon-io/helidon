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

import java.net.URI;
import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

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
 * If the Etcd ConfigSource is {@code mandatory} and a {@code uri} is not responsive or {@code key} does not exist
 * then {@link ConfigSource#load} throws {@link ConfigException}.
 * <p>
 * One of {@code media-type} and {@code parser} properties must be set to be clear how to parse the content. If both of them
 * are set, then {@code parser} has precedence.
 */
public final class EtcdConfigSourceBuilder
        extends AbstractParsableConfigSource.Builder<EtcdConfigSourceBuilder, EtcdEndpoint> {

    private static final String URI_KEY = "uri";
    private static final String KEY_KEY = "key";
    private static final String API_KEY = "api";
    private final EtcdEndpoint etcdEndpoint;

    private EtcdConfigSourceBuilder(URI uri, String key, EtcdApi api) {
        super(EtcdEndpoint.class);

        Objects.requireNonNull(uri, "uri cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(api, "api cannot be null");

        this.etcdEndpoint = new EtcdEndpoint(uri, key, api);
    }

    /**
     * Create new instance of builder with specified mandatory Etcd endpoint remote descriptor.
     *
     * @param uri an Etcd endpoint remote URI.
     * @param key an Etcd key with which the value containing the configuration is associated.
     * @param api an Etcd API version.
     * @return new instance of builder
     * @see #from(Config)
     */
    public static EtcdConfigSourceBuilder from(URI uri, String key, EtcdApi api) {
        return new EtcdConfigSourceBuilder(uri, key, api);
    }

    /**
     * Initializes config source instance from meta configuration properties,
     * see {@link io.helidon.config.ConfigSources#load(Config)}.
     * <p>
     * Mandatory {@code properties}, see {@link #from(URI, String, EtcdApi)}:
     * <ul>
     * <li>{@code uri} - type {@link URI}</li>
     * <li>{@code key} - type {@code String}</li>
     * <li>{@code api} - type {@link EtcdApi}, e.g. {@code v3}</li>
     * </ul>
     * Optional {@code properties}: see {@link #init(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source builder instance from.
     * @return new instance of config source builder described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see #from(URI, String, EtcdApi)
     * @see #init(Config)
     */
    public static EtcdConfigSourceBuilder from(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return EtcdConfigSourceBuilder.from(metaConfig.get(URI_KEY).as(URI.class),
                                            metaConfig.get(KEY_KEY).asString(),
                                            metaConfig.get(API_KEY).as(EtcdApi.class))
                .init(metaConfig);
    }

    @Override
    protected EtcdConfigSourceBuilder init(Config metaConfig) {
        return super.init(metaConfig);
    }

    @Override
    protected EtcdEndpoint getTarget() {
        return etcdEndpoint;
    }

    PollingStrategy getPollingStrategyInternal() { //just for testing purposes
        return super.getPollingStrategy();
    }

    /**
     * Builds new instance of Etcd ConfigSource.
     * <p>
     * If the Etcd ConfigSource is {@code mandatory} and a {@code uri} is not responsive or {@code key} does not exist
     * then {@link ConfigSource#load} throws {@link ConfigException}.
     *
     * @return new instance of Etcd ConfigSource.
     */
    @Override
    public ConfigSource build() {
        return new EtcdConfigSource(this);
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
        v2,

        /**
         * Etcd API v3 version.
         */
        v3

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
         * @param key an etcd key with which the value containing the configuration is associated.
         * @param api an Etcd API version.
         */
        public EtcdEndpoint(URI uri, String key, EtcdApi api) {
            this.uri = uri;
            this.key = key;
            this.api = api;
        }

        public URI getUri() {
            return uri;
        }

        public String getKey() {
            return key;
        }

        public EtcdApi getApi() {
            return api;
        }
    }
}
