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

package io.helidon.webserver.observe.log;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.logging.common.spi.LogLevelManager;
import io.helidon.logging.common.spi.LoggerLevel;

class JulLogLevelManager implements LogLevelManager {
    private static final List<String> LEVELS = List.of(Level.OFF.getName(),
                                                       Level.SEVERE.getName(),
                                                       Level.WARNING.getName(),
                                                       Level.INFO.getName(),
                                                       Level.FINE.getName(),
                                                       Level.FINER.getName(),
                                                       Level.FINEST.getName());

    private final LogManager logManager = LogManager.getLogManager();
    private final Logger root = Logger.getLogger("");

    @Override
    public List<String> levels() {
        return LEVELS;
    }

    @Override
    public Map<String, LoggerLevel> loggers() {
        Map<String, LoggerLevel> result = new LinkedHashMap<>();

        Enumeration<String> loggerNames = logManager.getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            LoggerLevel logger = logger(loggerNames.nextElement()).orElseThrow();
            result.put(logger.name(), logger);
        }

        return result;
    }

    @Override
    public Optional<LoggerLevel> logger(String name) {
        Logger logger = Logger.getLogger(toJulName(name));

        Level configuredLevel = logger.getLevel();
        Level effectiveLevel = effectiveLevel(logger);

        return Optional.of(LoggerLevel.create(toResponseName(logger.getName()),
                                             effectiveLevel.getName(),
                                             configuredLevel == null ? null : configuredLevel.getName()));
    }

    @Override
    public void setLevel(String name, String level) {
        Logger.getLogger(toJulName(name))
                .setLevel(Level.parse(level));
    }

    @Override
    public void unsetLevel(String name) {
        Logger.getLogger(toJulName(name))
                .setLevel(null);
    }

    private Level effectiveLevel(Logger logger) {
        Level level = logger.getLevel();
        if (level == null) {
            if (logger == root) {
                return Level.INFO;
            }

            Logger parent = logger.getParent();
            if (parent == null) {
                return effectiveLevel(root);
            }

            return effectiveLevel(parent);
        }
        return level;
    }

    private static String toJulName(String name) {
        return LogLevelManager.ROOT_LOGGER_NAME.equals(name) ? "" : name;
    }

    private static String toResponseName(String name) {
        return name.isEmpty() ? LogLevelManager.ROOT_LOGGER_NAME : name;
    }
}
