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

package io.helidon.logging.log4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.logging.common.spi.LogLevelManager;
import io.helidon.logging.common.spi.LoggerLevel;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Runtime logger level management for Log4j.
 */
public class Log4jLogLevelManager implements LogLevelManager {
    private static final List<String> LEVELS = List.of(Level.OFF.name(),
                                                       Level.FATAL.name(),
                                                       Level.ERROR.name(),
                                                       Level.WARN.name(),
                                                       Level.INFO.name(),
                                                       Level.DEBUG.name(),
                                                       Level.TRACE.name(),
                                                       Level.ALL.name());

    @Override
    public List<String> levels() {
        return LEVELS;
    }

    @Override
    public Map<String, LoggerLevel> loggers() {
        Map<String, LoggerLevel> result = new LinkedHashMap<>();
        LoggerContext context = context();
        Configuration configuration = context.getConfiguration();

        configuration.getLoggers()
                .keySet()
                .forEach(name -> putLogger(result, configuration, name));
        context.getLoggers()
                .forEach(logger -> putLogger(result, configuration, logger.getName()));
        putLogger(result, configuration, LogManager.ROOT_LOGGER_NAME);

        return result;
    }

    @Override
    public Optional<LoggerLevel> logger(String name) {
        return Optional.of(logger(context().getConfiguration(), toLog4jName(name)));
    }

    @Override
    public void setLevel(String name, String level) {
        String loggerName = toLog4jName(name);
        Level log4jLevel = Level.valueOf(level);
        LoggerContext context = context();
        Configuration configuration = context.getConfiguration();
        if (isRoot(loggerName)) {
            configuration.getRootLogger().setLevel(log4jLevel);
            context.updateLoggers();
            return;
        }

        LoggerConfig loggerConfig = configuration.getLoggers().get(loggerName);

        if (loggerConfig == null) {
            LoggerConfig parent = configuration.getLoggerConfig(loggerName);
            loggerConfig = new LoggerConfig(loggerName, log4jLevel, true);
            loggerConfig.setParent(parent);
            configuration.addLogger(loggerName, loggerConfig);
        } else {
            loggerConfig.setLevel(log4jLevel);
        }
        context.updateLoggers();
    }

    @Override
    public void unsetLevel(String name) {
        LoggerContext context = context();
        Configuration configuration = context.getConfiguration();
        String loggerName = toLog4jName(name);
        LoggerConfig loggerConfig = isRoot(loggerName)
                ? configuration.getRootLogger()
                : configuration.getLoggers().get(loggerName);

        if (loggerConfig != null) {
            loggerConfig.setLevel(null);
            context.updateLoggers();
        }
    }

    private void putLogger(Map<String, LoggerLevel> result, Configuration configuration, String name) {
        LoggerLevel logger = logger(configuration, name);
        result.putIfAbsent(logger.name(), logger);
    }

    private LoggerLevel logger(Configuration configuration, String name) {
        LoggerConfig configuredLogger = isRoot(name)
                ? configuration.getRootLogger()
                : configuration.getLoggers().get(name);
        LoggerConfig effectiveLogger = isRoot(name)
                ? configuration.getRootLogger()
                : configuration.getLoggerConfig(name);
        Level effectiveLevel = effectiveLogger.getLevel();
        Level configuredLevel = configuredLogger == null ? null : configuredLogger.getExplicitLevel();

        return LoggerLevel.create(toResponseName(name),
                                  effectiveLevel == null ? Level.ERROR.name() : effectiveLevel.name(),
                                  configuredLevel == null ? null : configuredLevel.name());
    }

    private static LoggerContext context() {
        return (LoggerContext) LogManager.getContext(false);
    }

    private static String toLog4jName(String name) {
        return LogLevelManager.ROOT_LOGGER_NAME.equals(name) ? LogManager.ROOT_LOGGER_NAME : name;
    }

    private static String toResponseName(String name) {
        return name.isEmpty() ? LogLevelManager.ROOT_LOGGER_NAME : name;
    }

    private static boolean isRoot(String name) {
        return name.isEmpty();
    }
}
