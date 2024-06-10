/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.common.testing.junit5;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures log records in memory for later retrieval.
 * <p>
 *     The handler is intended to be attached to a single logger. Callers can use the static factory methods to create an
 *     in-memory handler and attach it to the specified logger. The handler is auto-closable so a properly-constructed
 *     test--using try-with-resource--will automatically clear the handler's log records and detach the handler from the logger.
 * </p>
 */
public class InMemoryLoggingHandler extends Handler implements AutoCloseable {

    /**
     * Creates a new in-memory logging handler and attaches it to the specified logger.
     *
     * @param logger the {@link java.util.logging.Logger} to which to add a new in-memory handler
     * @return the new handler
     */
    public static InMemoryLoggingHandler create(Logger logger) {
        return new InMemoryLoggingHandler(logger);
    }

    /**
     * Creates a new in-memory logging handler and attaches it to the logger named after the class name of the specified object.
     *
     * @param objectWithNamedLogger the object whose class-name-based logger the handler should be added to
     * @return the new handler
     */
    public static InMemoryLoggingHandler create(Object objectWithNamedLogger) {
        return create(Logger.getLogger(objectWithNamedLogger.getClass().getName()));
    }

    private final Logger logger;
    private final List<LogRecord> logRecords = new ArrayList<>();
    private final List<LogRecord> unmodifiableLogRecords = Collections.unmodifiableList(logRecords);

    private InMemoryLoggingHandler(Logger logger) {
        this.logger = logger;
        logger.addHandler(this);
    }


    @Override
    public void publish(LogRecord record) {
        logRecords.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        logRecords.clear();
        logger.removeHandler(this);
    }

    /**
     * Returns the accumulated log records.
     *
     * @return list of log records
     */
    public List<LogRecord> logRecords() {
        return unmodifiableLogRecords;
    }
}
