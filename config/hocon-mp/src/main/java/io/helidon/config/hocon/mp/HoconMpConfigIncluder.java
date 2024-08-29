/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.hocon.mp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigParserException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;

import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

class HoconMpConfigIncluder implements ConfigIncluder {
    private static final System.Logger LOGGER = System.getLogger(HoconMpConfigIncluder.class.getName());
    private static final String HOCON_EXTENSION = ".conf";

    private ConfigParseOptions parseOptions;
    private String relativeUrl;
    private Path relativePath;
    private Charset charset;

    HoconMpConfigIncluder() {
    }

    @Override
    public ConfigIncluder withFallback(ConfigIncluder fallback) {
        return this;
    }

    @Override
    public ConfigObject include(ConfigIncludeContext context, String what) {
        LOGGER.log(TRACE, String.format("Received request to include resource %s, %s",
                what, context.parseOptions().getOriginDescription()));

        return relativeUrl != null ? parseHoconFromUrl(what) : parseHoconFromPath(what);
    }

    private ConfigObject parseHoconFromUrl(String includeName) {
        String includePath = relativeUrl + patchName(includeName);
        URL includeUrl;
        try {
            includeUrl = new URL(includePath);
        } catch (MalformedURLException e) {
            LOGGER.log(WARNING, String.format("Unable to create include Url for: %s with error: %s",
                    includePath, e.getMessage()));
            return ConfigFactory.empty().root();
        }
        try (InputStreamReader readable = new InputStreamReader(includeUrl.openConnection().getInputStream(), charset)) {
            Config typesafeConfig = ConfigFactory.parseReader(readable, parseOptions);
            return typesafeConfig.root();
        } catch (IOException e) {
            throw new ConfigParserException("Failed to read from include source: " + includeUrl, e);
        }
    }

    private ConfigObject parseHoconFromPath(String includeName) {
        Path path = relativePath.resolve(includeName);
        if (Files.exists(path) && Files.isReadable(path) && !Files.isDirectory(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
                Config typesafeConfig = ConfigFactory.parseReader(reader, parseOptions);
                return typesafeConfig.root();
            } catch (IOException e) {
                throw new ConfigException("Failed to read from include source: " + path.toAbsolutePath(), e);
            }
        } else {
            return ConfigFactory.empty().root();
        }
    }

    void charset(Charset charset) {
        this.charset = charset;
    }

    void parseOptions(ConfigParseOptions parseOptions) {
        this.parseOptions = parseOptions;
    }

    void relativeUrl(String relativeUrl) {
        this.relativeUrl = relativeUrl;
    }

    void relativePath(Path relativePath) {
        this.relativePath = relativePath;
    }

    /**
     * Adds default Hocon extension if not present.
     *
     * @param what file name
     * @return file name with extension
     */
    private static String patchName(String what) {
        Optional<String> base = Optional.of(what)
                .filter(f -> f.contains(File.separator))
                .map(f -> f.substring(f.lastIndexOf(File.separator) + 1));
        Optional<String> ext = Optional.of(base.orElse(what))
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".") + 1));
        return ext.isPresent() ? what : what + HOCON_EXTENSION;
    }
}
