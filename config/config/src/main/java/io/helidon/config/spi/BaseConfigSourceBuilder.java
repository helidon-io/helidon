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
 */

package io.helidon.config.spi;

import java.util.Optional;

import io.helidon.config.Config;

public abstract class BaseConfigSourceBuilder<B extends BaseConfigSourceBuilder<B, U>, U>
        extends BaseSourceBuilder<B, U>
        implements ConfigSource.Builder<B> {

    private ConfigParser parser;
    private String mediaType;
    @SuppressWarnings("unchecked")
    private B me = (B) this;

    protected B config(Config metaConfig) {
        super.config(metaConfig);
        // TODO read parser and media type if configured
        return me;
    }

    protected B parser(ConfigParser parser) {
        this.parser = parser;
        return me;
    }

    protected B mediaType(String mediaType) {
        this.mediaType = mediaType;
        return me;
    }

    Optional<ConfigParser> parser() {
        return Optional.ofNullable(parser);
    }

    Optional<String> mediaType() {
        return Optional.ofNullable(mediaType);
    }
}
