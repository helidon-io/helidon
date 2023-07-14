/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.spi;

import java.util.ServiceLoader;

import io.helidon.dbclient.DbClient;

/**
 * Java {@link ServiceLoader} interface that provides drivers for a database (or a set of databases).
 * This is DbClient SPI entry point which returns unique name of the provider and an implementation
 * of the {@link DbClient} builder, the {@link DbClientBuilder} interface.
 */
public interface DbClientProvider {

    /**
     * Name of this provider. This is used to find correct provider when using configuration only approach.
     *
     * @return provider name (such as {@code jdbc} or {@code mongo}
     */
    String name();

    /**
     * The implementation should provide its implementation of the {@link DbClientBuilder}.
     *
     * @return a new builder instance
     */
    DbClientBuilder<?> builder();

}
