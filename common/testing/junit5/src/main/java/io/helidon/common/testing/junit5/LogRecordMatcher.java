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

import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Hamcrest matcher for a {@link java.util.logging.LogRecord}.
 */
public class LogRecordMatcher {

    private static final Formatter SIMPLE_LOG_FORMATTER = new SimpleFormatter();

    /**
     * Returns a matcher for a {@link java.util.logging.LogRecord} which checks if its {@code thrown} setting satisfies the
     * specified throwable matcher.
     *
     * @param matcher matcher of a throwable to apply to the log record's {@code thrown} value
     * @return the log record matcher to apply the throwable matcher
     */
    public static Matcher<LogRecord> withThrown(Matcher<? super Throwable> matcher) {
        return new WithThrown(matcher);
    }

    /**
     * Returns a matcher for a {@link java.util.logging.LogRecord} which checks if its {@code message} satisfies the specified
     * string matcher.
     *
     * @param matcher matcher of a string to apply to the log record's {@code message} value
     * @return the log record matcher to apply the string matcher
     */
    public static Matcher<LogRecord> withMessage(Matcher<? super String> matcher) {
        return new WithMessage(matcher);
    }

    private LogRecordMatcher() {
    }

    private static class WithThrown extends TypeSafeMatcher<LogRecord> {

        private final Matcher<? super Throwable> matcher;

        WithThrown(Matcher<? super Throwable> matcher) {
            this.matcher = matcher;
        }

        @Override
        protected void describeMismatchSafely(LogRecord item, Description mismatchDescription) {
            mismatchDescription.appendText("log record ");
            matcher.describeMismatch(logRecordToString(item), mismatchDescription);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("log record with thrown ");
            description.appendDescriptionOf(matcher);
        }

        @Override
        protected boolean matchesSafely(LogRecord item) {
            return matcher.matches(item.getThrown());
        }
    }

    private static class WithMessage extends TypeSafeMatcher<LogRecord> {

        private final Matcher<? super String> matcher;

        WithMessage(Matcher<? super String> matcher) {
            this.matcher = matcher;
        }

        @Override
        protected boolean matchesSafely(LogRecord item) {
            return matcher.matches(item.getMessage());
        }

        @Override
        protected void describeMismatchSafely(LogRecord item, Description mismatchDescription) {
            mismatchDescription.appendText("log record ");
            matcher.describeMismatch(logRecordToString(item), mismatchDescription);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("log record with message ");
            description.appendDescriptionOf(matcher);
        }
    }

    private static String logRecordToString(LogRecord logRecord) {
        return SIMPLE_LOG_FORMATTER.format(logRecord);
    }
}
