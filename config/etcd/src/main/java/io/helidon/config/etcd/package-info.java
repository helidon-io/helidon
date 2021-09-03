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

/**
 * Etcd configuration source.
 * <p>
 * This module contains Etcd ConfigSource which allow user to retrieve a configuration stored as a
 * single
 * entry in etcd.
 * There is also a {@link io.helidon.config.etcd.EtcdConfigSourceBuilder} for a convenient way how to initialize Etcd ConfigSource.
 * The {@code EtcdConfigSourceBuilder} allows to specify following properties (besides the ones covered in
 * {@link io.helidon.config.AbstractConfigSourceBuilder}) a {@code uri} where the instance of Etcd is running,
 * the {@code key} where the configuration is stored, {@code version} of the Etcd API which should be used.
 * <p>
 * Etcd integration is placed in {@code io.helidon.config.etcd} Java 9 module.
 * Maven coordinates are {@code io.helidon.config:helidon-config-etcd}.
 *
 * @see io.helidon.config.etcd.EtcdConfigSource#builder()
 * @see io.helidon.config.etcd.EtcdWatcher
 */
package io.helidon.config.etcd;
