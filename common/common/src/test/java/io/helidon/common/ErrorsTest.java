/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Errors}.
 */
class ErrorsTest {
    private static final Logger LOGGER = Logger.getLogger(ErrorsTest.class.getName());

    private static void assertErrorMessage(Optional<Errors.ErrorMessage> actual, String expected, String message) {
        assertThat(actual, not(Optional.empty()));
        assertThat(actual.get().getMessage(), is(expected));
    }

    @Test
    void testErrorCollection() {
        String fatalMessage = "This object is no longer valid. Expired on 2017.04.10.";
        String warnMessage = "Algorithm is recommended. Filled in default value: RS256";
        String hintMessage = "Description should be filled in";

        Errors.Collector collector = Errors.collector();
        collector.fatal(fatalMessage);
        collector.warn(warnMessage);
        collector.hint(hintMessage);

        Errors errors = collector.collect();

        errors.log(LOGGER);

        assertErrorMessage(errors.stream()
                                   .filter(it -> it.getSeverity() == Severity.FATAL)
                                   .findFirst(),
                           fatalMessage,
                           "Fatal message should be present");

        assertErrorMessage(errors.stream()
                                   .filter(it -> it.getSeverity() == Severity.WARN)
                                   .findFirst(),
                           warnMessage,
                           "Warn message should be present");

        assertErrorMessage(errors.stream()
                                   .filter(it -> it.getSeverity() == Severity.HINT)
                                   .findFirst(),
                           hintMessage,
                           "Hint message should be present");

        assertThat(errors.hasFatal(), is(true));
        assertThat(errors.hasWarning(), is(true));
        assertThat(errors.hasHint(), is(true));

        assertThat(errors.isValid(), is(false));

        assertThat(errors.log(LOGGER), is(false));

        Errors.ErrorMessagesException thrown = assertThrows(Errors.ErrorMessagesException.class,
                                                            errors::checkValid);
        assertThat(thrown.getMessages(), sameInstance(errors));
    }

    @Test
    void testSingleMessage() {
        String messageContent = "A message";
        Errors errors = Errors.collector()
                .message(messageContent, Severity.HINT)
                .collect();

        assertThat(errors.hasFatal(), is(false));
        assertThat(errors.hasWarning(), is(false));
        assertThat(errors.hasHint(), is(true));

        assertThat(errors.isValid(), is(true));

        errors.checkValid();

        assertThat(errors.log(LOGGER), is(true));
        assertThat(errors.size(), is(1));

        Errors.ErrorMessage msg = errors.get(0);
        assertThat(msg.getMessage(), is(messageContent));
        assertThat(msg.getSeverity(), is(Severity.HINT));
        assertThat(msg.getSource(), sameInstance(ErrorsTest.class));
    }

    @Test
    void testSingleHint() {
        String messageContent = "A message";
        Errors errors = Errors.collector()
                .hint(messageContent)
                .collect();

        assertThat(errors.hasFatal(), is(false));
        assertThat(errors.hasWarning(), is(false));
        assertThat(errors.hasHint(), is(true));

        assertThat(errors.isValid(), is(true));

        errors.checkValid();

        assertThat(errors.log(LOGGER), is(true));
        assertThat(errors.size(), is(1));

        Errors.ErrorMessage msg = errors.get(0);
        assertThat(msg.getMessage(), is(messageContent));
        assertThat(msg.getSeverity(), is(Severity.HINT));
        assertThat(msg.getSource(), sameInstance(ErrorsTest.class));
    }

    @Test
    void testSingleFatal() {
        String messageContent = "A message";
        Errors errors = Errors.collector()
                .fatal(messageContent)
                .collect();

        assertThat(errors.hasFatal(), is(true));
        assertThat(errors.hasWarning(), is(false));
        assertThat(errors.hasHint(), is(false));

        assertThat(errors.isValid(), is(false));

        Errors.ErrorMessagesException thrown = assertThrows(Errors.ErrorMessagesException.class,
                                                            errors::checkValid);
        assertThat(thrown.getMessages(), sameInstance(errors));

        assertThat(errors.log(LOGGER), is(false));
        assertThat(errors.size(), is(1));

        Errors.ErrorMessage msg = errors.get(0);
        assertThat(msg.getMessage(), is(messageContent));
        assertThat(msg.getSeverity(), is(Severity.FATAL));
        assertThat(msg.getSource(), sameInstance(ErrorsTest.class));
    }

    @Test
    void testSingleWarn() {
        String messageContent = "Warning!";
        String source = "some source";
        Errors errors = Errors.collector()
                .warn(source, messageContent)
                .collect();

        assertThat(errors.hasFatal(), is(false));
        assertThat(errors.hasWarning(), is(true));
        assertThat(errors.hasHint(), is(false));

        assertThat(errors.isValid(), is(true));

        errors.checkValid();

        assertThat(errors.log(LOGGER), is(true));
        assertThat(errors.size(), is(1));

        Errors.ErrorMessage msg = errors.get(0);
        assertThat(msg.getMessage(), is(messageContent));
        assertThat(msg.getSeverity(), is(Severity.WARN));
        assertThat(msg.getSource(), sameInstance(source));
    }

    @Test
    void testReuse() {
        String messageContent = "Warning!";
        String source = "some source";
        Errors errors = Errors.collector()
                .warn(source, messageContent)
                .clear()
                .hint(source, messageContent)
                .collect();

        assertThat(errors.hasFatal(), is(false));
        assertThat(errors.hasWarning(), is(false));
        assertThat(errors.hasHint(), is(true));

        assertThat(errors.isValid(), is(true));

        errors.checkValid();

        assertThat(errors.log(LOGGER), is(true));
        assertThat(errors.size(), is(1));

        Errors.ErrorMessage msg = errors.get(0);
        assertThat(msg.getMessage(), is(messageContent));
        assertThat(msg.getSeverity(), is(Severity.HINT));
        assertThat(msg.getSource(), sameInstance(source));
    }

    @Test
    void testNone() {
        Errors errors = Errors.collector().collect();

        assertThat(errors.hasFatal(), is(false));
        assertThat(errors.hasWarning(), is(false));
        assertThat(errors.hasHint(), is(false));

        assertThat(errors.isValid(), is(true));

        errors.checkValid();

        assertThat(errors.log(LOGGER), is(true));
        assertThat(errors.size(), is(0));
    }

    @Test
    void testEquals() {
        Errors.ErrorMessage msg1 = new Errors.ErrorMessage("source", "message", Severity.WARN);
        Errors.ErrorMessage msg2 = new Errors.ErrorMessage("source", "message", Severity.WARN);
        Errors.ErrorMessage msg3 = new Errors.ErrorMessage("source2", "message", Severity.WARN);
        Errors.ErrorMessage msg4 = new Errors.ErrorMessage("source", "message2", Severity.WARN);
        Errors.ErrorMessage msg5 = new Errors.ErrorMessage("source", "message", Severity.HINT);

        assertThat(msg1, is(msg1));
        assertThat(msg1, is(msg2));

        testNotEquals(msg1, null);
        testNotEquals(msg1, msg3);
        testNotEquals(msg3, msg1);
        testNotEquals(msg1, msg4);
        testNotEquals(msg4, msg1);
        testNotEquals(msg1, msg5);
        testNotEquals(msg5, msg1);
    }

    private void testNotEquals(Errors.ErrorMessage msg1, Errors.ErrorMessage msg2) {
        assertThat(msg1, not(msg2));
    }
}
