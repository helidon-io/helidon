/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link StreamHandler} that writes to {@link System#out standard out} and uses a {@link ThreadFormatter} for formatting.
 * Sets the level to {@link Level#ALL} so that level filtering is performed solely by the loggers.
 *
 * @deprecated use io.helidon.logging.jul.HelidonConsoleHandler from helidon-logging-jul module instead
 */
@Deprecated(since = "2.1.1")
public class HelidonConsoleHandler extends StreamHandler {

    /**
     * Creates a new {@link HelidonConsoleHandler} configured with:
     * <ul>
     *     <li>the output stream set to {@link System#out}</li>
     *     <li>the formatter set to a {@link ThreadFormatter}</li>
     *     <li>the level set to {@link Level#ALL}</li>
     * </ul>.
     */
    public HelidonConsoleHandler() {
        setOutputStream(System.out);
        setLevel(Level.ALL); // Handlers should not filter, loggers should
        setFormatter(new ThreadFormatter());
        // we need to decide how to handle all of our examples and templates, before warning users
        /*
        System.out.println("You are using deprecated logging handler -> io.helidon.common.HelidonConsoleHandler "
        + "Please use helidon-logging-jul module and change your handler to "
        + "io.helidon.logging.jul.HelidonConsoleHandler");
        */
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
     * A {@link SimpleFormatter} that replaces all occurrences of {@code "!thread!"} with the current thread.
     */
    public static class ThreadFormatter extends SimpleFormatter {
        private static final Pattern THREAD_PATTERN = Pattern.compile("!thread!");
        private static final String JUL_FORMAT_PROP_KEY = "java.util.logging.SimpleFormatter.format";
        private static final String DEFAULT_FORMAT =
                "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n";

        private final String format = configuredFormat();

        private static String sanitizeLogValue(String value) {
            return value == null ? null : value.replace("\r", "\\r").replace("\n", "\\n");
        }

        private static String configuredFormat() {
            String configured = systemProperty(JUL_FORMAT_PROP_KEY);
            if (configured == null) {
                configured = LogManager.getLogManager().getProperty(JUL_FORMAT_PROP_KEY);
            }
            if (configured == null) {
                return DEFAULT_FORMAT;
            }
            try (Formatter formatter = new Formatter()) {
                formatter.format(configured, ZonedDateTime.now(), "", "", "", "", "");
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

        @Override
        public String format(LogRecord record) {
            String message = formatRow(record);
            return THREAD_PATTERN.matcher(message)
                    .replaceAll(Matcher.quoteReplacement(sanitizeLogValue(Thread.currentThread().toString())));
        }

        //Copied from SimpleFormatter
        private String formatRow(LogRecord record) {
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
}
