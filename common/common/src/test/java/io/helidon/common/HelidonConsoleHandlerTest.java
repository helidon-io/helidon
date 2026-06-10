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

package io.helidon.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

class HelidonConsoleHandlerTest {
    private static final String JUL_FORMAT_PROP_KEY = "java.util.logging.SimpleFormatter.format";

    private static String formatWithThreadFormatter(LogRecord record) throws Exception {
        return formatWithThreadFormatter(record, "%5$s%6$s%n");
    }

    private static String formatWithThreadFormatter(LogRecord record, String format) throws Exception {
        return formatWithThreadFormatter(record, format, null);
    }

    private static String formatWithThreadFormatter(LogRecord record,
                                                    String format,
                                                    String systemFormat) throws Exception {
        LogManager logManager = LogManager.getLogManager();
        String originalFormat = System.getProperty(JUL_FORMAT_PROP_KEY);
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream captureOut = new PrintStream(captured, true, StandardCharsets.UTF_8);
        try {
            if (systemFormat == null) {
                System.clearProperty(JUL_FORMAT_PROP_KEY);
            } else {
                System.setProperty(JUL_FORMAT_PROP_KEY, systemFormat);
            }
            logManager.readConfiguration(new ByteArrayInputStream(
                    (JUL_FORMAT_PROP_KEY + "=" + format + "\n").getBytes(StandardCharsets.UTF_8)));

            System.setOut(captureOut);
            HelidonConsoleHandler handler = new HelidonConsoleHandler();
            handler.publish(record);
            handler.close();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
            if (originalFormat == null) {
                System.clearProperty(JUL_FORMAT_PROP_KEY);
            } else {
                System.setProperty(JUL_FORMAT_PROP_KEY, originalFormat);
            }
            logManager.readConfiguration();
            captureOut.close();
        }
    }

    @Test
    void testMessageNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        LogRecord record = new LogRecord(Level.WARNING, "original warning\r\n" + forgedLine);

        String formatted = formatWithThreadFormatter(record);

        assertThat(formatted, containsString("original warning\\r\\n" + forgedLine));
        assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
    }

    @Test
    void testParameterNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        LogRecord record = new LogRecord(Level.WARNING, "original warning {0}");
        record.setParameters(new Object[] {"original parameter\r\n" + forgedLine});

        String formatted = formatWithThreadFormatter(record);

        assertThat(formatted, containsString("original parameter\\r\\n" + forgedLine));
        assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
    }

    @Test
    void testNonStringParameterNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        Object parameter = new Object() {
            @Override
            public String toString() {
                return "original parameter\r\n" + forgedLine;
            }
        };
        LogRecord record = new LogRecord(Level.WARNING, "original warning {0}");
        record.setParameters(new Object[] {parameter});

        String formatted = formatWithThreadFormatter(record);

        assertThat(formatted, containsString("original parameter\\r\\n" + forgedLine));
        assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
    }

    @Test
    void testThrowableNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        LogRecord record = new LogRecord(Level.WARNING, "original warning");
        record.setThrown(new IllegalStateException("original throwable\r\n" + forgedLine) {
            @Override
            public String toString() {
                return "custom throwable: " + getMessage();
            }
        });

        String formatted = formatWithThreadFormatter(record);

        assertThat(formatted, containsString("custom throwable: original throwable\\r\\n" + forgedLine));
        assertThat(formatted, not(containsString("SanitizedThrowable")));
        assertThat(formatted, containsString(System.lineSeparator() + "\tat "));
        assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
    }

    @Test
    void testThrowableStackFrameNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        IllegalStateException throwable = new IllegalStateException("original throwable");
        throwable.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("test.DeclaringClass\r\n" + forgedLine,
                                      "testMethod\r\n" + forgedLine,
                                      "TestFile.java\r\n" + forgedLine,
                                      42)
        });
        LogRecord record = new LogRecord(Level.WARNING, "original warning");
        record.setThrown(throwable);

        String formatted = formatWithThreadFormatter(record);

        assertThat(formatted, containsString("test.DeclaringClass\\r\\n" + forgedLine));
        assertThat(formatted, containsString("testMethod\\r\\n" + forgedLine));
        assertThat(formatted, containsString("TestFile.java\\r\\n" + forgedLine));
        assertThat(Arrays.asList(formatted.split("\\R")).stream()
                           .anyMatch(line -> line.startsWith(forgedLine)), is(false));
    }

    @Test
    void testCustomThrowablePrintStackTraceNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        LogRecord record = new LogRecord(Level.WARNING, "original warning");
        record.setThrown(new IllegalStateException("original throwable") {
            @Override
            public void printStackTrace(PrintWriter writer) {
                writer.println("custom throwable\r\n" + forgedLine);
            }
        });

        String formatted = formatWithThreadFormatter(record);

        assertThat(formatted, containsString("custom throwable\\r\\n" + forgedLine));
        assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
    }

    @Test
    void testLevelNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        LogRecord record = new LogRecord(new Level("WARNING\r\n" + forgedLine, Level.WARNING.intValue()) {
            private static final long serialVersionUID = 1L;
        }, "original warning");

        String formatted = formatWithThreadFormatter(record, "%4$s %5$s%n");

        assertThat(formatted, containsString("WARNING\\r\\n" + forgedLine));
        assertThat(Arrays.asList(formatted.split("\\R")).stream()
                           .anyMatch(line -> line.startsWith(forgedLine)), is(false));
    }

    @Test
    void testLevelLocalizedNameOverrideIsNotCalled() throws Exception {
        LogRecord record = new LogRecord(new Level("CUSTOM", Level.WARNING.intValue()) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getLocalizedName() {
                throw new AssertionError("getLocalizedName should not be called");
            }
        }, "original warning");

        String formatted = formatWithThreadFormatter(record, "%4$s %5$s%n");

        assertThat(formatted, is("CUSTOM original warning" + System.lineSeparator()));
    }

    @Test
    void testStandardLevelLocalizedNameIsPreserved() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            LogRecord record = new LogRecord(Level.WARNING, "original warning");

            String formatted = formatWithThreadFormatter(record, "%4$s %5$s%n");

            assertThat(formatted, is(Level.WARNING.getLocalizedName() + " original warning" + System.lineSeparator()));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void testCustomResourceBundleLevelFallsBackToNonLocalizedName() throws Exception {
        LogRecord record = new LogRecord(new Level("CUSTOM_BUNDLE", 901, "test.bundle") {
            private static final long serialVersionUID = 1L;
        }, "original warning");

        String formatted = formatWithThreadFormatter(record, "%4$s %5$s%n");

        assertThat(formatted, is("CUSTOM_BUNDLE original warning" + System.lineSeparator()));
    }

    @Test
    void testCustomStandardNameLevelFallsBackToNonLocalizedName() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            LogRecord record = new LogRecord(new Level("WARNING", Level.WARNING.intValue()) {
                private static final long serialVersionUID = 1L;
            }, "original warning");

            String formatted = formatWithThreadFormatter(record, "%4$s %5$s%n");

            assertThat(formatted, is("WARNING original warning" + System.lineSeparator()));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void testThreadReplacementEscapesNewLines() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        Thread thread = Thread.currentThread();
        String originalName = thread.getName();
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        try {
            thread.setName("original thread\r\n" + forgedLine + "$\\");

            String formatted = formatWithThreadFormatter(record, "%5$s !thread!%n");

            assertThat(formatted, containsString("original thread\\r\\n" + forgedLine + "$\\"));
            assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
        } finally {
            thread.setName(originalName);
        }
    }

    @Test
    void testSystemPropertyFormatOverridesLogManagerFormat() throws Exception {
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        String formatted = formatWithThreadFormatter(record, "%4$s config %5$s%n", "%4$s system %5$s%n");

        assertThat(formatted, is("WARNING system original warning" + System.lineSeparator()));
    }

    @Test
    void testInvalidFormatFallsBackToDefaultFormat() throws Exception {
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        String formatted = formatWithThreadFormatter(record, "%Q");

        assertThat(formatted, containsString("WARNING: original warning"));
    }
}
