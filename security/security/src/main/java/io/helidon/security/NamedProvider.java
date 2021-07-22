/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security;

import io.helidon.security.spi.SecurityProvider;

/**
 * A wrapper for a named security provider.
 *
 * @param <T> Type of provider
 */
public final class NamedProvider<T extends SecurityProvider> {
    private final String name;
    private final T provider;

    NamedProvider(String name, T provider) {
        this.name = name;
        this.provider = provider;
    }

    /**
     * Name of this provider.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Provider instance.
     *
     * @return provider
     */
    public T getProvider() {
        return provider;
    }
}
