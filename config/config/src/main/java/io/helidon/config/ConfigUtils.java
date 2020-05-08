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

package io.helidon.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.config.spi.ConfigNode;

/**
 * Internal config utilities.
 */
final class ConfigUtils {

    private static final Logger LOGGER = Logger.getLogger(ConfigUtils.class.getName());

    private ConfigUtils() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Builds map into object node.
     * <p>
     * Dots in keys are interpreted as tree-structure separators.
     *
     * @param map    source map
     * @param strict In strict mode, properties overlapping causes failure during loading into internal structure.
     * @return built object node from map source.
     */
    static ConfigNode.ObjectNode mapToObjectNode(Map<?, ?> map, boolean strict) {
        ConfigNode.ObjectNode.Builder builder = ConfigNode.ObjectNode.builder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            try {
                builder.addValue(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            } catch (ConfigException ex) {
                if (strict) {
                    throw ex;
                } else {
                    LOGGER.log(Level.CONFIG, "Tree-structure failure on key '" + entry.getKey() + "', reason: "
                            + ex.getLocalizedMessage());
                    LOGGER.log(Level.FINEST, "Detailed reason of failure of adding key '" + entry.getKey()
                            + "' = '" + entry.getValue() + "'.", ex);
                }
            }
        }
        return builder.build();
    }

    /**
     * Transforms {@link java.util.Properties} to {@code Map<String, String>}.
     * <p>
     * It iterates just {@link Properties#stringPropertyNames() string property names} and uses it's
     * {@link Properties#getProperty(String) string value}.
     *
     * @param properties properties to be transformed to map
     * @return transformed map
     */
    static Map<String, String> propertiesToMap(Properties properties) {
        return properties.stringPropertyNames().stream()
                .collect(Collectors.toMap(k -> k, properties::getProperty));
    }

    /**
     * Shutdowns {@code executor} and waits for it.
     *
     * @param executor executor to be shutdown.
     */
    static void shutdownExecutor(ScheduledExecutorService executor) {
        executor.shutdown();
        try {
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    /**
     * Returns a {@link Charset} instance parsed from specified {@code content-encoding} HTTP response header
     * or {@code UTF-8} if the header is missing.
     *
     * @param contentEncoding {@code content-type} HTTP response header
     * @return {@link Charset} parsed from {@code contentEncoding}
     * or {@code UTF-8} in case a {@code contentEncoding} is {@code null}
     * @throws ConfigException in case of unsupported charset name
     */
    static Charset getContentCharset(String contentEncoding) throws ConfigException {
        try {
            return Optional.ofNullable(contentEncoding)
                    .map(Charset::forName)
                    .orElse(StandardCharsets.UTF_8);
        } catch (UnsupportedCharsetException ex) {
            throw new ConfigException("Unsupported response content-encoding '" + contentEncoding + "'.", ex);
        }
    }
}
