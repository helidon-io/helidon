/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.hocon;

import java.util.Objects;

import io.helidon.common.Builder;
import io.helidon.config.spi.ConfigParser;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigResolveOptions;

/**
 * HOCON ConfigParser Builder.
 * <p>
 * {@link Config#resolve() HOCON resolving substitutions support} is by default enabled.
 * {@link ConfigResolveOptions#defaults()} is used to resolve loaded configuration.
 * It is possible to {@link #disableResolving() disable resolving} feature
 * or specify custom {@link #resolveOptions(ConfigResolveOptions) ConfigResolveOptions} instance.
 */
public final class HoconConfigParserBuilder implements Builder<ConfigParser> {

    private boolean resolvingEnabled;
    private ConfigResolveOptions resolveOptions;

    HoconConfigParserBuilder() {
        resolvingEnabled = true;
        resolveOptions = ConfigResolveOptions.defaults();
    }

    /**
     * Disables HOCON resolving substitutions support.
     *
     * @return modified builder instance
     */
    public HoconConfigParserBuilder disableResolving() {
        resolvingEnabled = false;
        return this;
    }

    /**
     * Sets custom instance of {@link ConfigResolveOptions}.
     * <p>
     * By default {@link ConfigResolveOptions#defaults()} is used.
     *
     * @param resolveOptions resolve options
     * @return modified builder instance
     */
    public HoconConfigParserBuilder resolveOptions(ConfigResolveOptions resolveOptions) {
        this.resolveOptions = Objects.requireNonNull(resolveOptions);
        return this;
    }

    /**
     * Builds new instance of HOCON ConfigParser.
     *
     * @return new instance of HOCON ConfigParser.
     */
    @Override
    public HoconConfigParser build() {
        return new HoconConfigParser(resolvingEnabled, resolveOptions);
    }

}
