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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime logger level management for a logging backend.
 */
public interface LogLevelManager {
    /**
     * Logger name used by observability responses for the root logger.
     */
    String ROOT_LOGGER_NAME = "ROOT";

    /**
     * Supported level names.
     *
     * @return level names supported by this backend
     */
    List<String> levels();

    /**
     * Known loggers.
     *
     * @return known loggers, keyed by logger name
     */
    Map<String, LoggerLevel> loggers();

    /**
     * Logger by name.
     *
     * @param name logger name
     * @return logger level information, or empty if this backend does not know this logger
     */
    Optional<LoggerLevel> logger(String name);

    /**
     * Set a logger level.
     *
     * @param name logger name
     * @param level level name
     */
    void setLevel(String name, String level);

    /**
     * Unset the configured logger level.
     *
     * @param name logger name
     */
    void unsetLevel(String name);
}
