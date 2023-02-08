/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.util.Set;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.nima.webserver.http1.spi.Http1Upgrader;
import io.helidon.nima.websocket.webserver.WsUpgradeProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for upgrade from HTTP/1.1 to Tyrus connection.
 */
public class TyrusUpgradeProvider extends WsUpgradeProvider {

    TyrusUpgradeProvider(Builder builder) {
        super(builder);
    }

    /**
     * @deprecated This constructor is only to be used by {@link java.util.ServiceLoader}, use {@link #builder()}
     */
    @Deprecated()
    public TyrusUpgradeProvider() {
        this(tyrusBuilder());
    }

    /**
     * New builder.
     *
     * @return builder
     */
    public static Builder tyrusBuilder() {
        return new Builder();
    }

    @Override
    public Http1Upgrader create(Function<String, Config> config) {
        Set<String> usedOrigins;

        if (origins().isEmpty()) {
            usedOrigins = config.apply(CONFIG_NAME)
                    .get("origins")
                    .asList(String.class)
                    .map(Set::copyOf)
                    .orElseGet(Set::of);
        } else {
            usedOrigins = origins();
        }

        return new TyrusUpgrader(usedOrigins);
    }

    // jUnit test accessor for origins set (package private only)
    protected Set<String> origins() {
        return super.origins();
    }

    /**
     * Fluent API builder for {@link TyrusUpgradeProvider}.
     */
    public static final class Builder
            extends WsUpgradeProvider.AbstractBuilder<TyrusUpgradeProvider.Builder, TyrusUpgradeProvider> {

        private Builder() {
        }

        @Override
        public TyrusUpgradeProvider build() {
            return new TyrusUpgradeProvider(this);
        }

    }

}
