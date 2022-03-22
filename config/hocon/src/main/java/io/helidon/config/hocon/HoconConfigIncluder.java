/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.spi.ConfigParserException;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;

import static java.lang.System.Logger.Level.TRACE;

class HoconConfigIncluder implements ConfigIncluder {
    private static final System.Logger LOGGER = System.getLogger(HoconConfigIncluder.class.getName());
    private ConfigParseOptions parseOptions;
    private Function<String, Optional<InputStream>> relativeResourceFunction;
    private Charset charset;

    HoconConfigIncluder() {
    }

    @Override
    public ConfigIncluder withFallback(ConfigIncluder fallback) {
        return this;
    }

    @Override
    public ConfigObject include(ConfigIncludeContext context, String what) {
        LOGGER.log(TRACE, String.format("Received request to include resource %s, %s",
                                        what, context.parseOptions().getOriginDescription()));
        Optional<InputStream> maybeStream = relativeResourceFunction.apply(what);
        if (maybeStream.isEmpty()) {
            if (Objects.nonNull(context.parseOptions()) && !context.parseOptions().getAllowMissing()) {
                throw new ConfigParserException(what + " is missing");
            }
            return ConfigFactory.empty().root();
        }

        try (InputStreamReader readable = new InputStreamReader(maybeStream.get(), charset)) {
            return ConfigFactory.parseReader(readable, parseOptions).root();
        } catch (IOException e) {
            throw new ConfigParserException("Failed to read from source: " + what, e);
        }
    }

    void charset(Charset charset) {
        this.charset = charset;
    }

    void parseOptions(ConfigParseOptions parseOptions) {
        this.parseOptions = parseOptions;
    }

    void relativeResourceFunction(Function<String, Optional<InputStream>> relativeResourceFunction) {
        this.relativeResourceFunction = relativeResourceFunction;
    }

}
