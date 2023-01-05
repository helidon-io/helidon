/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.services;

import java.lang.System.Logger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationLogEntry;
import io.helidon.pico.ActivationLogQuery;

/**
 * The default reference implementation of {@link io.helidon.pico.ActivationLog} and {@link io.helidon.pico.ActivationLogQuery}.
 */
class DefaultActivationLog implements ActivationLog, ActivationLogQuery {
    private final List<ActivationLogEntry> log;
    private final Logger logger;
    private Logger.Level level;

    private DefaultActivationLog(
            List<ActivationLogEntry> log,
            Logger logger,
            Logger.Level level) {
        this.log = log;
        this.logger = logger;
        this.level = level;
    }

    /**
     * Create a retained activation log that tee's to the provided logger. A retained log is capable of supporting
     * {@link io.helidon.pico.ActivationLogQuery}.
     *
     * @param logger the logger to use
     * @return the created activity log
     */
    static DefaultActivationLog createRetainedLog(
            Logger logger) {
        return new DefaultActivationLog(new CopyOnWriteArrayList<>(), logger, Logger.Level.INFO);
    }

    /**
     * Create a unretained activation log that simply logs to the provided logger. An unretained log is not capable of
     * supporting {@link io.helidon.pico.ActivationLogQuery}.
     *
     * @param logger the logger to use
     * @return the created activity log
     */
    static DefaultActivationLog createUnretainedLog(
            Logger logger) {
        return new DefaultActivationLog(null, logger, Logger.Level.DEBUG);
    }

    /**
     * Sets the logging level.
     *
     * @param level the level
     */
    public void level(
            Logger.Level level) {
        this.level = level;
    }

    @Override
    public ActivationLogEntry record(
            ActivationLogEntry entry) {
        if (log != null) {
            log.add(Objects.requireNonNull(entry));
        }

        if (logger != null) {
            logger.log(level, entry);
        }

        return entry;
    }

    @Override
    public Optional<ActivationLogQuery> toQuery() {
        return (log != null) ? Optional.of(this) : Optional.empty();
    }

    @Override
    public boolean reset(
            boolean ignored) {
        if (null != log) {
            boolean affected = !log.isEmpty();
            log.clear();
            return affected;
        }

        return false;
    }

    @Override
    public List<ActivationLogEntry> fullActivationLog() {
        return (null == log) ? List.of() : List.copyOf(log);
    }

}
