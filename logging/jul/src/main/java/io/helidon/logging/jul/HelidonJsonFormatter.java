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

package io.helidon.logging.jul;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.logging.common.HelidonMdc;
import io.helidon.metadata.hson.Hson;

import static io.helidon.logging.jul.HelidonFormatter.JUL_FORMAT_PROP_KEY;
import static io.helidon.logging.jul.HelidonFormatter.THREAD_TOKEN;

/**
 * A {@link java.util.logging.Formatter} that stores each log record as a single-line JSON value.
 * It also replaces all occurrences of MDC tags like {@code %X{value}} with specific values,
 * and supports replacement of {@code "!thread!"} with the current thread.
 * <p>
 * The configuration should be done through property {@value #JSON_FORMAT_PROP_KEY}, that provides comma separated list of name to
 * fields to log (references are the same as you would use in a {@link java.util.logging.SimpleFormatter}).
 * <p>
 * The configuration falls back to property {@value io.helidon.logging.jul.HelidonFormatter#JUL_FORMAT_PROP_KEY},
 * and analyzes it to provide a "guessed" JSON structure.
 * <p>
 * Example (and also the default format):<br>
 * {@value #DEFAULT_FORMAT}
 */
public class HelidonJsonFormatter extends Formatter {
    static final String DEFAULT_FORMAT = "ts:%1$tQ,date:%1$tY.%1$tm.%1$td,time:%1$tH:%1$tM:%1$tS.%1$tL,level:%4$s,message:%5$s,"
            + "exception:%6$s,thread:!thread!,logger:%3$s";

    private static final String JSON_FORMAT_PROP_KEY = "io.helidon.logging.jul.HelidonJsonFormatter.fields";

    // formats we understand
    private static final String EPOCH_MILLIS_FORMAT = "%1$tQ";
    private static final String YEAR_FORMAT = "%1$tY";
    private static final String MONTH_FORMAT = "%1$tm";
    private static final String DAY_FORMAT = "%1$td";
    private static final String HOUR_FORMAT = "%1$tH";
    private static final String MINUTE_FORMAT = "%1$tM";
    private static final String SECOND_FORMAT = "%1$tS";
    private static final String SOURCE_FORMAT = "%2$s";
    private static final String LOGGER_FORMAT = "%3$s";
    private static final String LEVEL_FORMAT = "%4$s";
    private static final String MESSAGE_FORMAT = "%5$s";
    private static final String EXCEPTION_FORMAT = "%6$s";

    private final List<ValueFormatter> formatters;

    /**
     * Create new instance of the {@link io.helidon.logging.jul.HelidonJsonFormatter}.
     */
    public HelidonJsonFormatter() {
        String jsonFormat = LogManager.getLogManager().getProperty(JSON_FORMAT_PROP_KEY);
        String julFormat = LogManager.getLogManager().getProperty(JUL_FORMAT_PROP_KEY);
        if (jsonFormat == null && julFormat == null) {
            jsonFormat = DEFAULT_FORMAT;
        }
        if (jsonFormat == null) {
            this.formatters = guessFromSimpleFormat(julFormat);
        } else {
            this.formatters = fromJsonFormat(jsonFormat);
        }
    }

    HelidonJsonFormatter(String format, boolean jsonFormat) {
        this.formatters = jsonFormat ? fromJsonFormat(format) : guessFromSimpleFormat(format);
    }

    @Override
    public String format(LogRecord record) {
        var builder = Hson.Struct.builder();
        var params = HelidonFormatter.parameters(record, super.formatMessage(record));

        formatters.forEach(formatter -> formatter.update(builder, params));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        builder.build()
                .write(pw);
        pw.println();
        pw.close();
        return sw.toString();
    }

    private List<ValueFormatter> fromJsonFormat(String jsonFormat) {
        return Stream.of(jsonFormat.split(","))
                .map(it -> new ValueFormatter(Field.create(jsonFormat, it)))
                .collect(Collectors.toUnmodifiableList());
    }

    private List<ValueFormatter> guessFromSimpleFormat(String julFormat) {
        List<ValueFormatter> result = new ArrayList<>();
        Map<String, AtomicInteger> counters = new HashMap<>();
        String usedFormat = julFormat.replaceAll("%n", " ");
        // spaces expected to separate "blocks"
        for (String block : usedFormat.split(" ")) {
            if (block.isBlank()) {
                continue;
            }
            // now lets do some "magic"
            if (block.contains(YEAR_FORMAT) && block.contains(MONTH_FORMAT) && block.contains(DAY_FORMAT)) {
                if (block.contains(HOUR_FORMAT)) {
                    // full timestamp
                    result.add(new ValueFormatter(new Field(name(counters, "timestamp"), block)));
                } else {
                    // date only
                    result.add(new ValueFormatter(new Field(name(counters, "date"), block)));
                }
                continue;
            }
            if (block.contains(HOUR_FORMAT) && block.contains(MINUTE_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "time"), block)));
                continue;
            }

            // now only create sections if it only contains one parameter
            if (block.contains(SOURCE_FORMAT) && block.length() < 7) {
                result.add(new ValueFormatter(new Field(name(counters, "source"), block)));
                continue;
            }
            if (block.contains(LOGGER_FORMAT) && block.length() < 7) {
                result.add(new ValueFormatter(new Field(name(counters, "logger"), block)));
                continue;
            }
            if (block.contains(LEVEL_FORMAT) && block.length() < 7) {
                result.add(new ValueFormatter(new Field(name(counters, "level"), block)));
                continue;
            }
            if (block.contains(MESSAGE_FORMAT) && block.length() < 7) {
                result.add(new ValueFormatter(new Field(name(counters, "message"), block)));
                continue;
            }
            if (block.contains(EXCEPTION_FORMAT) && block.length() < 7) {
                result.add(new ValueFormatter(new Field(name(counters, "exception"), block)));
                continue;
            }
            if (block.contains(THREAD_TOKEN) && block.length() < THREAD_TOKEN.length() + 3) {
                result.add(new ValueFormatter(new Field(name(counters, "thread"), block)));
                continue;
            }

            // now let's extract the parts
            if (block.contains(EPOCH_MILLIS_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "ts"), EPOCH_MILLIS_FORMAT)));
            }
            if (block.contains(YEAR_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "year"), YEAR_FORMAT)));
            }
            if (block.contains(MONTH_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "month"), MONTH_FORMAT)));
            }
            if (block.contains(DAY_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "day"), DAY_FORMAT)));
            }
            if (block.contains(HOUR_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "hour"), HOUR_FORMAT)));
            }
            if (block.contains(MINUTE_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "minute"), MINUTE_FORMAT)));
            }
            if (block.contains(SECOND_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "second"), SECOND_FORMAT)));
            }
            if (block.contains(SOURCE_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "source"), SOURCE_FORMAT)));
            }
            if (block.contains(LOGGER_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "logger"), LOGGER_FORMAT)));
            }
            if (block.contains(LEVEL_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "level"), LEVEL_FORMAT)));
            }
            if (block.contains(MESSAGE_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "message"), MESSAGE_FORMAT)));
            }
            if (block.contains(EXCEPTION_FORMAT)) {
                result.add(new ValueFormatter(new Field(name(counters, "exception"), EXCEPTION_FORMAT)));
            }
            if (block.contains(THREAD_TOKEN) || block.contains("%X{" + HelidonFormatter.THREAD + "}")) {
                result.add(new ValueFormatter(new Field(name(counters, "thread"), THREAD_TOKEN)));
            }

            // MDC support
            Matcher matcher = HelidonFormatter.X_VALUE.matcher(usedFormat);
            while (matcher.find()) {
                String name = matcher.group(2);
                if (!name.equals(HelidonFormatter.THREAD)) {
                    result.add(new ValueFormatter(new Field(name(counters, "X." + name), "%X{" + name + "}")));
                }
            }
        }
        return result;
    }

    private String name(Map<String, AtomicInteger> counters, String name) {
        if (counters.containsKey(name)) {
            return name + "_" + counters.get(name).incrementAndGet();
        } else {
            counters.put(name, new AtomicInteger());
            return name;
        }
    }

    private static class ValueFormatter {
        private final Set<String> parsedProps = new HashSet<>();
        private final String jsonName;
        private final String format;
        private final boolean thread;

        private ValueFormatter(Field field) {
            this.jsonName = field.name();
            this.format = field.format();

            this.thread = this.format.contains(THREAD_TOKEN) || this.format.contains("%X{" + HelidonFormatter.THREAD + "}");
            Matcher matcher = HelidonFormatter.X_VALUE.matcher(this.format);
            while (matcher.find()) {
                parsedProps.add(matcher.group(2));
            }
        }

        private void update(Hson.Struct.Builder jsonBuilder, Object... parameters) {

            String message = thread ? HelidonFormatter.thread(format) : format;
            for (String parsedKey : parsedProps) {
                String value = HelidonMdc.get(parsedKey).orElse("");
                message = HelidonFormatter.PATTERN_CACHE
                        .computeIfAbsent(parsedKey, key -> Pattern.compile("%X\\{" + key + "}"))
                        .matcher(message)
                        .replaceAll(value);
            }
            String formattedValue = String.format(message, parameters);
            if (!formattedValue.isBlank()) {
                jsonBuilder.set(jsonName, formattedValue);
            }
        }
    }

    private record Field(String name, String format) {
        private static Field create(String format, String field) {
            int index = field.indexOf(':');
            if (index == -1) {
                throw new IllegalArgumentException("Invalid format definition for " + HelidonJsonFormatter.class.getSimpleName()
                                                           + ", each field must have field name followed by a colon with field "
                                                           + "value,"
                                                           + " such as 'message:%5$s', but got: '" + field + "'. "
                                                           + "Full format: " + format);
            }
            return new Field(field.substring(0, index), field.substring(index + 1));
        }
    }
}
