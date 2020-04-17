/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import io.helidon.config.Config;
import io.helidon.config.MissingValueException;

import static io.helidon.webserver.cors.Aggregator.PATHLESS_KEY;

/**
 * SE implementation of {@link CorsSupport}.
 */
public class CorsSupportSe extends CorsSupport {

    private CorsSupportSe(Builder builder) {
        super(builder);
    }

    /**
     *
     * @return new builder for CorsSupportSe
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     *
     * @return new CorsSupportSe with default settings
     */
    public static CorsSupportSe create() {
        return builder().build();
    }

    /**
     * Creates a new {@code CorsSupportSe} instance based on the provided configuration expected to match the basic
     * {@code CrossOriginConfig} format.
     *
     * @param config node containing the cross-origin information
     * @return initialized {@code CorsSupportSe} instance
     */
    public static CorsSupportSe from(Config config) {
        if (!config.exists()) {
            throw MissingValueException.create(config.key());
        }
        Builder builder = builder().addCrossOrigin(PATHLESS_KEY, CrossOriginConfig.builder(config).build());
        return builder.build();
    }

    public static class Builder extends CorsSupport.Builder<CorsSupportSe, Builder> {

        @Override
        public CorsSupportSe build() {
            return new CorsSupportSe(this);
        }

        @Override
        protected Builder me() {
            return this;
        }
    }
}
