/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.logging.common.spi;

import java.util.Optional;

/**
 * Runtime level information for a logger.
 */
public final class LoggerLevel {
    private final String name;
    private final String level;
    private final String configuredLevel;

    private LoggerLevel(String name, String level, String configuredLevel) {
        this.name = name;
        this.level = level;
        this.configuredLevel = configuredLevel;
    }

    /**
     * Create logger level information.
     *
     * @param name logger name
     * @param level effective level
     * @param configuredLevel configured level, or {@code null} if inherited
     * @return logger level information
     */
    public static LoggerLevel create(String name, String level, String configuredLevel) {
        return new LoggerLevel(name, level, configuredLevel);
    }

    /**
     * Logger name.
     *
     * @return logger name
     */
    public String name() {
        return name;
    }

    /**
     * Effective logger level.
     *
     * @return effective level
     */
    public String level() {
        return level;
    }

    /**
     * Configured logger level, if explicitly configured.
     *
     * @return configured level
     */
    public Optional<String> configuredLevel() {
        return Optional.ofNullable(configuredLevel);
    }
}
