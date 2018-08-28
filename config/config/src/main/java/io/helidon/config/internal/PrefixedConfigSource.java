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

package io.helidon.config.internal;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.OptionalHelper;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;

/**
 * {@link ConfigSource} implementation wraps another config source and add key prefix to original one.
 *
 * @see io.helidon.config.ConfigSources#prefixed(String, java.util.function.Supplier)
 */
public class PrefixedConfigSource implements ConfigSource {

    private final String key;
    private final ConfigSource source;

    /**
     * Initialize prefixed config source.
     *
     * @param key    prefix key
     * @param source wrapped source
     */
    public PrefixedConfigSource(String key, ConfigSource source) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        this.key = key;
        this.source = source;
    }

    @Override
    public String description() {
        return String.format("prefixed[%s]:%s", key, source.description());
    }

    @Override
    public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
        return OptionalHelper.from(source.load()
                .map(originRoot -> new ObjectNodeBuilderImpl().addObject(key, originRoot).build()))
                .or(Optional::empty)
                .asOptional();
    }

    @Override
    public void init(ConfigContext context) {
        source.init(context);
    }

}
