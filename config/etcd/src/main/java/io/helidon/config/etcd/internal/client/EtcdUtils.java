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

package io.helidon.config.etcd.internal.client;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.ConfigException;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.internal.client.v2.EtcdV2Client;
import io.helidon.config.etcd.internal.client.v3.EtcdV3Client;

/**
 * Provides utility methods for etcd client package.
 */
public class EtcdUtils {

    private EtcdUtils() {
    }

    private static final Map<EtcdApi, Class<? extends EtcdClient>> ETCD_API_VERSION_CLASS_MAP =
            Collections.unmodifiableMap(
                    new HashMap<EtcdApi, Class<? extends EtcdClient>>() {
                        {
                            put(EtcdApi.v2, EtcdV2Client.class);
                            put(EtcdApi.v3, EtcdV3Client.class);
                        }
                    }
            );

    /**
     * Returns an implementation class of etcd client for given etcd API version.
     *
     * @param etcdApiVersion etcd API version
     * @return an implementation class of etcd client
     */
    public static Class<? extends EtcdClient> getClientClass(EtcdApi etcdApiVersion) {
        return ETCD_API_VERSION_CLASS_MAP.get(etcdApiVersion);
    }

    /**
     * Creates a new instance of {@link EtcdClient}'s implementation specified by parameter {@code clientClass} by invoking
     * constructor with {@link URI uri} parameter.
     *
     * @param clientClass a client class
     * @param uri         an etcd uri
     * @return an instance of client
     * @throws ConfigException when client cannot be instantiated
     */
    public static EtcdClient getClient(Class<? extends EtcdClient> clientClass, URI uri) throws ConfigException {
        try {
            return clientClass.getDeclaredConstructor(URI.class).newInstance(uri);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new ConfigException(String.format("Cannot instantiate etcd client class %s.", clientClass.getName()), e);
        }
    }
}
