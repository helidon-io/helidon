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

/**
 * A base implementation for config sources, that combines configuration from any type of a config source.
 * This class does not directly implement the interfaces - this is left to the implementer of the config source.
 * This class provides configuration methods as {@code protected}, so you can make them public in your implementation, to only
 * expose methods that must be implemented.
 * <p>
 * Other methods of the config source interfaces must be implemented by each source as they are data specific,
 * such as {@link io.helidon.config.spi.PollableSource#isModified(Object)}.
 *
 * All other methods return reasonable defaults.
 * Config framework analyzes the config source based on interfaces it implements.
 *
 * @see io.helidon.config.spi.ConfigSource
 * @see io.helidon.config.spi.WatchableSource
 * @see io.helidon.config.spi.PollableSource
 * @see io.helidon.config.spi.ParsableSource
 */
public abstract class BaseConfigSource extends BaseSource implements ConfigSource {
    private final Optional<String> mediaType;
    private final Optional<ConfigParser> parser;

    @SuppressWarnings("unchecked")
    protected BaseConfigSource(BaseConfigSourceBuilder builder) {
        super(builder);
        this.mediaType = builder.mediaType();
        this.parser = builder.parser();
    }

    protected Optional<String> mediaType() {
        return mediaType;
    }

    protected Optional<ConfigParser> parser() {
        return parser;
    }
}
