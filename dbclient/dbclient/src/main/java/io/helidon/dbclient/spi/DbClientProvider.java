/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Java Service loader interface that provides drivers for a database (or a set of databases).
 */
public interface DbClientProvider {
    /**
     * Name of this provider. This is used to find correct provider when using configuration only approach.
     *
     * @return provider name (such as {@code jdbc} or {@code mongo}
     */
    String name();

    /**
     * The implementation should provide its implementation of the {@link DbClientProviderBuilder}.
     *
     * @param <T> type of the builder
     * @return a new builder instance
     */
    <T extends DbClientProviderBuilder<T>> T builder();
}
