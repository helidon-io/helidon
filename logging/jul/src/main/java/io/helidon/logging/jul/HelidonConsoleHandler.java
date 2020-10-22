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

package io.helidon.logging.jul;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;

/**
 * A {@link StreamHandler} that writes to {@link System#out standard out} and uses a {@link HelidonJulFormatter} for formatting.
 * Sets the level to {@link Level#ALL} so that level filtering is performed solely by the loggers.
 */
public class HelidonConsoleHandler extends StreamHandler {

    /**
     * Creates a new {@link HelidonConsoleHandler} configured with:
     * <ul>
     *     <li>the output stream set to {@link System#out}</li>
     *     <li>the formatter set to a {@link HelidonJulFormatter}</li>
     *     <li>the level set to {@link Level#ALL}</li>
     * </ul>.
     */
    public HelidonConsoleHandler() {
        setOutputStream(System.out);
        setLevel(Level.ALL); // Handlers should not filter, loggers should
        setFormatter(new HelidonJulFormatter());
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    @Override
    public void close() {
        flush();
    }

    /**
     * A {@link SimpleFormatter} that replaces all occurrences of MDC tags like {@code %X{value}} with specific values.
     * It also supports replacement of {@code "!thread!"} with the current thread.
     */
    static class HelidonJulFormatter extends SimpleFormatter {
        private static final Pattern THREAD_PATTERN = Pattern.compile("!thread!");
        private static final Pattern X_REPLACE = Pattern.compile("\\s?%X\\{\\S*?}");
        private static final Map<String, Pattern> PATTERN_CACHE = new HashMap<>();
        private static final String JUL_FORMAT_PROP_KEY = "java.util.logging.SimpleFormatter.format";
        private final String format = LogManager.getLogManager().getProperty(JUL_FORMAT_PROP_KEY);

        @Override
        public String format(LogRecord record) {
            String message = format;
            if (message.contains("%X{")) {
                for (Map.Entry<String, String> entry : JulMdc.properties().entrySet()) {
                    message = PATTERN_CACHE.computeIfAbsent(entry.getKey(), key -> Pattern.compile("%X\\{" + key + "}"))
                            .matcher(message).replaceAll(entry.getValue());
                }
            }
            message = X_REPLACE.matcher(message).replaceAll("");
            message = THREAD_PATTERN.matcher(message).replaceAll(Thread.currentThread().toString());
            return formatRow(record, message);
        }

        //Copied from SimpleFormatter
        private String formatRow(LogRecord record, String format) {
            ZonedDateTime zdt = ZonedDateTime.ofInstant(
                    record.getInstant(), ZoneId.systemDefault());
            String source;
            if (record.getSourceClassName() != null) {
                source = record.getSourceClassName();
                if (record.getSourceMethodName() != null) {
                    source += " " + record.getSourceMethodName();
                }
            } else {
                source = record.getLoggerName();
            }
            String message = formatMessage(record);
            String throwable = "";
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println();
                record.getThrown().printStackTrace(pw);
                pw.close();
                throwable = sw.toString();
            }
            return String.format(format,
                                 zdt,
                                 source,
                                 record.getLoggerName(),
                                 record.getLevel().getLocalizedName(),
                                 message,
                                 throwable);
        }
    }
}
