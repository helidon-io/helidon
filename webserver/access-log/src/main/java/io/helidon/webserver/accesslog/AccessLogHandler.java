/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver.accesslog;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Log handler to support separation of access log to its own file.
 * <p>
 * This is used with Java Logging. For other frameworks, such as {@code slf4j} or {@code log4j} you can use a bridge for
 * Java Logging and configure a separate file using implementation specific configuration.
 * <p>
 * Java util logging configuration example using this handler:
 * <pre>
 * # Configure the log handler (uses the same configuration options as FileHandler, ignores formatter
 * io.helidon.webserver.accesslog.AccessLogHandler.level=FINEST
 * io.helidon.webserver.accesslog.AccessLogHandler.pattern=access.log
 * io.helidon.webserver.accesslog.AccessLogHandler.append=true
 *
 * # Configure the logger
 * io.helidon.webserver.AccessLog.level=INFO
 * io.helidon.webserver.AccessLog.useParentHandlers=false
 * io.helidon.webserver.AccessLog.handlers=io.helidon.webserver.accesslog.AccessLogHandler
 * </pre>
 */
public class AccessLogHandler extends FileHandler {
    /**
     * Construct a default {@code AccessLogHandler}.  This will be configured
     * entirely from {@link java.util.logging.LogManager} properties (or their default values).
     * 
     * @exception IOException if there are IO problems opening the files.
     * @exception SecurityException  if a security manager exists and if
     *             the caller does not have <code>LoggingPermission("control"))</code>.
     * @exception NullPointerException if pattern property is an empty String.
     */
    public AccessLogHandler() throws IOException, SecurityException {
        super();
    }

    @Override
    public synchronized void setFormatter(Formatter newFormatter) throws SecurityException {
        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();
            }
        };
        super.setFormatter(formatter);
    }
}
