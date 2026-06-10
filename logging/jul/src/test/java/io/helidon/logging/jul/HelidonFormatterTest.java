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

package io.helidon.logging.jul;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import io.helidon.logging.common.HelidonMdc;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

class HelidonFormatterTest {
    private static final String JUL_FORMAT_PROP_KEY = "java.util.logging.SimpleFormatter.format";

    private static String formatWithHelidonFormatter(LogRecord record) throws Exception {
        return formatWithHelidonFormatter(record, "%5$s%6$s%n");
    }

    private static String formatWithHelidonFormatter(LogRecord record, String format) throws Exception {
        return formatWithHelidonFormatter(record, format, null);
    }

    private static String formatWithHelidonFormatter(LogRecord record,
                                                     String format,
                                                     String systemFormat) throws Exception {
        LogManager logManager = LogManager.getLogManager();
        String originalFormat = System.getProperty(JUL_FORMAT_PROP_KEY);
        try {
            if (systemFormat == null) {
                System.clearProperty(JUL_FORMAT_PROP_KEY);
            } else {
                System.setProperty(JUL_FORMAT_PROP_KEY, systemFormat);
            }
            logManager.readConfiguration(new ByteArrayInputStream(
                    (JUL_FORMAT_PROP_KEY + "=" + format + "\n").getBytes(StandardCharsets.UTF_8)));
            return new HelidonFormatter().format(record);
        } finally {
            if (originalFormat == null) {
                System.clearProperty(JUL_FORMAT_PROP_KEY);
            } else {
                System.setProperty(JUL_FORMAT_PROP_KEY, originalFormat);
            }
            logManager.readConfiguration();
        }
    }

    @Test
    void testThrowableMessageNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        LogRecord record = new LogRecord(Level.WARNING, "original warning");
        record.setThrown(new IllegalStateException("original error\r\n" + forgedLine) {
            @Override
            public String toString() {
                return "custom throwable: " + getMessage();
            }
        });

        String formatted = formatWithHelidonFormatter(record);

        assertThat(formatted, containsString("custom throwable: original error\\r\\n" + forgedLine));
        assertThat(formatted, not(containsString("SanitizedThrowable")));
        assertThat(formatted, containsString(System.lineSeparator() + "\tat "));
        assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
    }

    @Test
    void testThrowableStackFrameNewLinesAreEscaped() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        IllegalStateException throwable = new IllegalStateException("original error");
        throwable.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("test.DeclaringClass\r\n" + forgedLine,
                                      "testMethod\r\n" + forgedLine,
                                      "TestFile.java\r\n" + forgedLine,
                                      42)
        });
        LogRecord record = new LogRecord(Level.WARNING, "original warning");
        record.setThrown(throwable);

        String formatted = formatWithHelidonFormatter(record);

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
        record.setThrown(new IllegalStateException("original error") {
            @Override
            public void printStackTrace(PrintWriter writer) {
                writer.println("custom throwable\r\n" + forgedLine);
            }
        });

        String formatted = formatWithHelidonFormatter(record);

        assertThat(formatted, containsString("custom throwable\\r\\n" + forgedLine));
        assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
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

        String formatted = formatWithHelidonFormatter(record, "%4$s %5$s%n");

        assertThat(formatted, is("CUSTOM original warning" + System.lineSeparator()));
    }

    @Test
    void testStandardLevelLocalizedNameIsPreserved() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            LogRecord record = new LogRecord(Level.WARNING, "original warning");

            String formatted = formatWithHelidonFormatter(record, "%4$s %5$s%n");

            assertThat(formatted, is(Level.WARNING.getLocalizedName() + " original warning" + System.lineSeparator()));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void testTokenInsideFormatterDirectiveFallsBack() throws Exception {
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        String formatted = formatWithHelidonFormatter(record, "%1$!thread!s%n");

        assertThat(formatted, containsString("WARNING: original warning"));
    }

    @Test
    void testTokenInsideDateFormatterDirectiveFallsBack() throws Exception {
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        String formatted = formatWithHelidonFormatter(record, "%1$t!thread! %5$s%n");

        assertThat(formatted, containsString("WARNING: original warning"));
    }

    @Test
    void testCustomResourceBundleLevelFallsBackToNonLocalizedName() throws Exception {
        LogRecord record = new LogRecord(new Level("CUSTOM_BUNDLE", 901, "test.bundle") {
            private static final long serialVersionUID = 1L;
        }, "original warning");

        String formatted = formatWithHelidonFormatter(record, "%4$s %5$s%n");

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

            String formatted = formatWithHelidonFormatter(record, "%4$s %5$s%n");

            assertThat(formatted, is("WARNING original warning" + System.lineSeparator()));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void testFormatSubstitutionsEscapePercentBeforeFormatting() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        Thread thread = Thread.currentThread();
        String originalName = thread.getName();
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        try {
            HelidonMdc.set("test", "original mdc%n" + forgedLine);
            thread.setName("original thread%n" + forgedLine);

            String formatted = formatWithHelidonFormatter(record, "%5$s %X{test} !thread!%n");

            assertThat(formatted, containsString("original mdc%n" + forgedLine));
            assertThat(formatted, containsString("original thread%n" + forgedLine));
            assertThat(Arrays.asList(formatted.split("\\R")), not(hasItem(forgedLine)));
        } finally {
            HelidonMdc.remove("test");
            thread.setName(originalName);
        }
    }

    @Test
    void testFormatSubstitutionsDoNotReplaceInsertedValues() throws Exception {
        String forgedLine = "FORGED WARNING: attacker-controlled second line";
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        try {
            HelidonMdc.set("a", "%X{b}");
            HelidonMdc.set("b", "n" + forgedLine);

            String formatted = formatWithHelidonFormatter(record, "%5$s %X{a} %%X{b} %X{b}%n");

            assertThat(formatted, containsString("%X{b} %X{b} n" + forgedLine));
            assertThat(Arrays.asList(formatted.split("\\R")).stream()
                               .anyMatch(line -> line.startsWith(forgedLine)), is(false));
        } finally {
            HelidonMdc.remove("a");
            HelidonMdc.remove("b");
        }
    }

    @Test
    void testOddPercentEscapedMdcTokenIsReplaced() throws Exception {
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        try {
            HelidonMdc.set("traceId", "abc123");

            String formatted = formatWithHelidonFormatter(record, "%5$s %%%X{traceId}%n");

            assertThat(formatted, is("original warning %abc123" + System.lineSeparator()));
        } finally {
            HelidonMdc.remove("traceId");
        }
    }

    @Test
    void testSystemPropertyFormatOverridesLogManagerFormat() throws Exception {
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        try {
            HelidonMdc.set("test", "mdc-value");

            String formatted = formatWithHelidonFormatter(record,
                                                         "%4$s config %5$s %X{test}%n",
                                                         "%4$s system %5$s %X{test}%n");

            assertThat(formatted, is("WARNING system original warning mdc-value" + System.lineSeparator()));
        } finally {
            HelidonMdc.remove("test");
        }
    }

    @Test
    void testInvalidFormatFallsBackToDefaultFormat() throws Exception {
        LogRecord record = new LogRecord(Level.WARNING, "original warning");

        String formatted = formatWithHelidonFormatter(record, "%Q");

        assertThat(formatted, containsString("WARNING: original warning"));
    }
}
