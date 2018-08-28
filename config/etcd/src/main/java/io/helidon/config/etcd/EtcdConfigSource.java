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

import java.io.StringReader;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.OptionalHelper;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.etcd.internal.client.EtcdUtils;
import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigParser;

/**
 * A config source which loads a configuration document from Etcd.
 * <p>
 * Config source is initialized by {@link EtcdConfigSourceBuilder}.
 *
 * @see EtcdConfigSourceBuilder
 */
class EtcdConfigSource extends AbstractParsableConfigSource<Long> {

    private static final Logger LOGGER = Logger.getLogger(EtcdConfigSource.class.getName());

    private final EtcdEndpoint endpoint;
    private final EtcdClient client;

    EtcdConfigSource(EtcdConfigSourceBuilder builder) {
        super(builder);

        endpoint = builder.getTarget();
        client = EtcdUtils.getClient(EtcdUtils.getClientClass(endpoint.getApi()), endpoint.getUri());
    }

    @Override
    protected String getMediaType() {
        return OptionalHelper.from(Optional.ofNullable(super.getMediaType()))
                .or(this::probeContentType)
                .asOptional()
                .orElse(null);
    }

    private Optional<String> probeContentType() {
        return Optional.ofNullable(ConfigHelper.detectContentType(Paths.get(endpoint.getKey())));
    }

    @Override
    protected String uid() {
        return endpoint.getUri() + "#" + endpoint.getKey();
    }

    @Override
    protected Optional<Long> dataStamp() {
        try {
            return Optional.of(getEtcdClient().revision(endpoint.getKey()));
        } catch (EtcdClientException e) {
            return Optional.empty();
        }
    }

    @Override
    protected ConfigParser.Content<Long> content() throws ConfigException {
        String content = null;
        try {
            content = getEtcdClient().get(endpoint.getKey());
        } catch (EtcdClientException e) {
            LOGGER.log(Level.FINEST, "Get operation threw an exception.", e);
        }

        // a KV pair does not exist
        if (content == null) {
            throw new ConfigException(String.format("Key '%s' does not contain any value", endpoint.getKey()));
        }
        return ConfigParser.Content.from(new StringReader(content), getMediaType(), dataStamp());
    }

    EtcdEndpoint getEtcdEndpoint() {
        return endpoint;
    }

    EtcdClient getEtcdClient() {
        return client;
    }

}
