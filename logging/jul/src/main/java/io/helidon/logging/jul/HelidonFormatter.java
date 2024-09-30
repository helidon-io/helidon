/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.logging.common.HelidonMdc;

/**
 * A {@link SimpleFormatter} that replaces all occurrences of MDC tags like {@code %X{value}} with specific values.
 * It also supports replacement of {@code "!thread!"} with the current thread.
 */
public class HelidonFormatter extends SimpleFormatter {
    static final String THREAD = "thread";
    static final String THREAD_TOKEN = "!" + THREAD + "!";
    static final Pattern THREAD_PATTERN = Pattern.compile(THREAD_TOKEN);
    static final Pattern X_VALUE = Pattern.compile("(\\s?%X\\{)(\\S*?)(})");
    static final Map<String, Pattern> PATTERN_CACHE = new HashMap<>();
    static final String JUL_FORMAT_PROP_KEY = "java.util.logging.SimpleFormatter.format";

    private final String format = LogManager.getLogManager().getProperty(JUL_FORMAT_PROP_KEY);
    private final Set<String> parsedProps = new HashSet<>();
    private final boolean thread;

    /**
     * Create new instance of the {@link HelidonFormatter}.
     */
    public HelidonFormatter() {
        thread = format.contains(THREAD_TOKEN) || format.contains("%X{" + THREAD + "}");
        Matcher matcher = X_VALUE.matcher(format);
        while (matcher.find()) {
            parsedProps.add(matcher.group(2));
        }
    }

    /*
    Replace the thread pattern in the format with the current thread value
     */
    static String thread(String format) {
        String currentThread = Thread.currentThread().toString();
        String message = PATTERN_CACHE.computeIfAbsent(THREAD, key -> Pattern.compile("%X\\{" + THREAD + "}"))
                .matcher(format).replaceAll(currentThread);
        message = THREAD_PATTERN.matcher(message).replaceAll(currentThread);
        return message;
    }

    /*
    All parameters expected by simple formatter (and json formatter as well)
     */
    static Object[] parameters(LogRecord record, String formattedMessage) {
        var timestamp = ZonedDateTime.ofInstant(
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

        String throwable = "";
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }

        Object[] result = new Object[6];
        result[0] = timestamp;
        result[1] = source;
        result[2] = record.getLoggerName();
        result[3] = record.getLevel().getName();
        result[4] = formattedMessage;
        result[5] = throwable;

        return result;
    }

    @Override
    public String format(LogRecord record) {
        String message = thread ? thread(format) : format;
        for (String parsedKey : parsedProps) {
            String value = HelidonMdc.get(parsedKey).orElse("");
            message = PATTERN_CACHE.computeIfAbsent(parsedKey, key -> Pattern.compile("%X\\{" + key + "}"))
                    .matcher(message).replaceAll(value);
        }
        return formatRow(record, message);
    }

    private String formatRow(LogRecord record, String format) {
        return String.format(format,
                             parameters(record, super.formatMessage(record)));
    }
}
