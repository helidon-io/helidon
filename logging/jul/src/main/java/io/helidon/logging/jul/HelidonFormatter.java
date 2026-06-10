/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
import java.util.logging.Level;
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
    private static final String THREAD = "thread";
    private static final String THREAD_TOKEN = "!" + THREAD + "!";
    private static final Pattern FORMAT_TOKEN = Pattern.compile("(?<!%)((?:%%)*)%X\\{(\\S*?)}|"
                                                                        + Pattern.quote(THREAD_TOKEN));
    private static final String JUL_FORMAT_PROP_KEY = "java.util.logging.SimpleFormatter.format";
    private static final String DEFAULT_FORMAT =
            "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n";

    private final String format = configuredFormat();

    private static String sanitizeLogValue(String value) {
        return value == null ? null : value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String sanitizeFormatValue(String value) {
        String sanitized = sanitizeLogValue(value);
        return sanitized == null ? null : sanitized.replace("%", "%%");
    }

    private static String configuredFormat() {
        String configured = systemProperty(JUL_FORMAT_PROP_KEY);
        if (configured == null) {
            configured = LogManager.getLogManager().getProperty(JUL_FORMAT_PROP_KEY);
        }
        if (configured == null) {
            return DEFAULT_FORMAT;
        }
        try {
            String.format(formatForValidation(configured), ZonedDateTime.now(), "", "", "", "", "");
            return configured;
        } catch (IllegalArgumentException e) {
            return DEFAULT_FORMAT;
        }
    }

    private static String systemProperty(String name) {
        try {
            return System.getProperty(name);
        } catch (SecurityException e) {
            return null;
        }
    }

    private static String levelName(Level level) {
        if (level == Level.OFF) {
            return Level.OFF.getLocalizedName();
        } else if (level == Level.SEVERE) {
            return Level.SEVERE.getLocalizedName();
        } else if (level == Level.WARNING) {
            return Level.WARNING.getLocalizedName();
        } else if (level == Level.INFO) {
            return Level.INFO.getLocalizedName();
        } else if (level == Level.CONFIG) {
            return Level.CONFIG.getLocalizedName();
        } else if (level == Level.FINE) {
            return Level.FINE.getLocalizedName();
        } else if (level == Level.FINER) {
            return Level.FINER.getLocalizedName();
        } else if (level == Level.FINEST) {
            return Level.FINEST.getLocalizedName();
        } else if (level == Level.ALL) {
            return Level.ALL.getLocalizedName();
        }
        return level.toString();
    }

    private static String formatForValidation(String format) {
        Matcher matcher = FORMAT_TOKEN.matcher(format);
        StringBuffer validated = new StringBuffer();
        while (matcher.find()) {
            String escapedPercentPrefix = matcher.group(1);
            matcher.appendReplacement(validated, Matcher.quoteReplacement(escapedPercentPrefix == null
                                                                                   ? "!"
                                                                                   : escapedPercentPrefix + "!"));
        }
        matcher.appendTail(validated);
        return validated.toString();
    }

    @Override
    public String format(LogRecord record) {
        Matcher matcher = FORMAT_TOKEN.matcher(format);
        StringBuffer message = new StringBuffer();
        String currentThread = null;
        while (matcher.find()) {
            String escapedPercentPrefix = matcher.group(1);
            String key = matcher.group(2);
            String replacement;
            if (key == null || THREAD.equals(key)) {
                if (currentThread == null) {
                    currentThread = sanitizeFormatValue(Thread.currentThread().toString());
                }
                replacement = currentThread;
            } else {
                replacement = sanitizeFormatValue(HelidonMdc.get(key).orElse(""));
            }
            if (escapedPercentPrefix != null) {
                replacement = escapedPercentPrefix + replacement;
            }
            matcher.appendReplacement(message, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(message);
        return formatRow(record, message.toString());
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
        String message = sanitizeLogValue(formatMessage(record));
        String throwable = "";
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(new SanitizingPrintWriter(pw));
            pw.close();
            throwable = sw.toString();
        }
        return String.format(format,
                             zdt,
                             sanitizeLogValue(source),
                             sanitizeLogValue(record.getLoggerName()),
                             sanitizeLogValue(levelName(record.getLevel())),
                             message,
                             throwable);
    }

    private static final class SanitizingPrintWriter extends PrintWriter {
        private final PrintWriter delegate;

        private SanitizingPrintWriter(PrintWriter delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void write(int c) {
            if (c == '\r') {
                delegate.write("\\r");
            } else if (c == '\n') {
                delegate.write("\\n");
            } else {
                delegate.write(c);
            }
        }

        @Override
        public void write(char[] buf, int off, int len) {
            write(new String(buf, off, len));
        }

        @Override
        public void write(String s, int off, int len) {
            delegate.write(sanitizeLogValue(s.substring(off, off + len)));
        }

        @Override
        public void print(String s) {
            delegate.print(sanitizeLogValue(s));
        }

        @Override
        public void print(Object obj) {
            delegate.print(sanitizeLogValue(String.valueOf(obj)));
        }

        @Override
        public void println() {
            delegate.println();
        }

        @Override
        public void println(String x) {
            delegate.println(sanitizeLogValue(x));
        }

        @Override
        public void println(Object x) {
            delegate.println(sanitizeLogValue(String.valueOf(x)));
        }
    }
}
