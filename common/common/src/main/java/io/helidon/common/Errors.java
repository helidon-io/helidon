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

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Errors utility used to file processing messages (e.g. validation, provider, resource building errors, hint).
 * <p>
 * Use {@link #collector()} to collect message through methods {@link Collector#hint(String)}, {@link Collector#warn(String)},
 * and {@link Collector#fatal(String)} and their counterparts with source object (e.g. {@link Collector#fatal(Object, String)}.
 *
 * Once all messages are processed, return validation result from {@link Collector#collect()}.
 *
 * The consumer of validation messages can then check result with {@link #isValid()} to get simple boolean,
 * {@link #checkValid()} to throw exception if not valid and other methods for fine-grained access.
 *
 * This class also extends a {@link LinkedList} of {@link ErrorMessage ErrorMessages} to give you full access to
 * all reported messages.
 *
 * <p>
 * Example:
 * <pre>{@code
 * Errors.Collector collector = Errors.collector();
 * if (field == null) {
 *     collector.fatal(this, "Field 'field' is null");
 * }
 * doOtherValidations(collector);
 *
 * Errors errors = collector.collect();
 * if (errors.isValid()) {
 *   return Response.VALID;
 * } else {
 *   return Response.builder().errors(errors).build();
 * }
 * }</pre>
 */
@SuppressWarnings("WeakerAccess")
public final class Errors extends LinkedList<Errors.ErrorMessage> {
    private static final Set<StackWalker.Option> WALKER_OPTIONS =
            Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final boolean hasFatal;
    private final boolean hasWarning;
    private final boolean hasHint;

    private Errors(Collector collector) {
        this.addAll(collector.errors);
        this.hasFatal = collector.hasFatal;
        this.hasWarning = collector.hasWarning;
        this.hasHint = collector.hasHint;
    }

    /**
     * Create a new message collector.
     *
     * @return message collector to add messages to
     */
    public static Collector collector() {
        return new Collector();
    }

    /**
     * Check if a fatal message is part of these messages.
     *
     * @return true if there is at least one message with severity {@link Severity#FATAL}
     */
    public boolean hasFatal() {
        return hasFatal;
    }

    /**
     * Check if a warning message is part of these messages.
     *
     * @return true if there is at least one message with severity {@link Severity#WARN}
     */
    public boolean hasWarning() {
        return hasWarning;
    }

    /**
     * Check if a hint message is part of these messages.
     *
     * @return true if there is at least one message with severity {@link Severity#HINT}
     */
    public boolean hasHint() {
        return hasHint;
    }

    /**
     * Log supplied errors and return a status flag indicating whether the result
     * is OK or not (will return true for valid, false if {@link Severity#FATAL} is present).
     *
     * @param logger Util logger to log messages into
     * @return {@code true} if there are no fatal issues present in the collection, {@code false}
     * otherwise.
     */

    public boolean log(Logger logger) {
        if (!isEmpty()) {
            StringBuilder fatals = new StringBuilder("\n");
            StringBuilder warnings = new StringBuilder();
            StringBuilder hints = new StringBuilder();

            for (final ErrorMessage error : this) {
                switch (error.getSeverity()) {
                case FATAL:
                    fatals.append(error).append('\n');
                    break;
                case WARN:
                    warnings.append(error).append('\n');
                    break;
                case HINT:
                    hints.append(error).append('\n');
                    break;
                default:
                    hints.append(error).append('\n');
                    break;
                }
            }

            if (hasFatal) {
                fatals.append(warnings).append(hints);

                logger.severe("Fatal issues found: " + fatals);
            } else {
                if (warnings.length() > 0) {
                    logger.warning("Warnings found: \n" + warnings);
                }

                if (hints.length() > 0) {
                    logger.config("Hints found: \n" + hints);
                }
            }

            return !hasFatal;
        }
        return true;
    }

    @Override
    public String toString() {
        return this.stream()
                .map(ErrorMessage::toString)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Check if these messages are a valid result.
     *
     * @return true if there is NO message in {@link Severity#FATAL} severity
     */
    public boolean isValid() {
        return !hasFatal;
    }

    /**
     * Check if these messages are a valid result, throws exception if not.
     *
     * @throws ErrorMessagesException in case there is at least one {@link Severity#FATAL} severity message
     */
    public void checkValid() throws ErrorMessagesException {
        if (hasFatal) {
            throw new ErrorMessagesException(this);
        }
    }

    /**
     * A collector of {@link ErrorMessage}s. Use method {@link #collect()} to obtain {@link Errors} instance.
     */
    public static class Collector {
        private final List<ErrorMessage> errors = new LinkedList<>();
        private boolean hasFatal;
        private boolean hasWarning;
        private boolean hasHint;

        /**
         * Add a message to the list of messages.
         *
         * @param source   source of the message
         * @param message  text message of the message
         * @param severity indicates severity of added message
         * @return updated collector
         */
        public Collector message(final Object source, final String message, final Severity severity) {
            Objects.requireNonNull(message, "Message must be defined");
            Objects.requireNonNull(severity, "Severity must be defined");

            this.errors.add(new ErrorMessage(((null == source) ? "unknown" : source), message, severity));
            switch (severity) {
            case FATAL:
                hasFatal = true;
                break;
            case WARN:
                hasWarning = true;
                break;
            case HINT:
                hasHint = true;
                break;
            default:
                // nothing to do here
                break;
            }

            return this;
        }

        /**
         * Add a message to the list of messages with source automatically added.
         *
         * @param message  text message of the message
         * @param severity indicates severity of added message
         * @return updated collector
         */
        public Collector message(final String message, final Severity severity) {
            return message(StackWalker.getInstance(WALKER_OPTIONS).getCallerClass(), message, severity);
        }

        /**
         * Add a fatal error to the list of messages.
         *
         * @param message message of the error
         * @return updated collector
         */
        public Collector fatal(String message) {
            return fatal(StackWalker.getInstance(WALKER_OPTIONS).getCallerClass(), message);
        }

        /**
         * Add a fatal error to the list of messages.
         *
         * @param source  source of the error
         * @param message message of the error
         * @return updated collector
         */
        public Collector fatal(Object source, String message) {
            return message(source, message, Severity.FATAL);
        }

        /**
         * Add a warning message to the list of messages.
         *
         * @param message message of the warning
         * @return updated collector
         */
        public Collector warn(String message) {
            return warn(StackWalker.getInstance(WALKER_OPTIONS).getCallerClass(), message);
        }

        /**
         * Add a warning message to the list of messages.
         *
         * @param source  source of the warning
         * @param message message of the warning
         * @return updated collector
         */
        public Collector warn(Object source, String message) {
            return message(source, message, Severity.WARN);
        }

        /**
         * Add a hint message to the list of messages.
         *
         * @param message message of the hint
         * @return updated collector
         */
        public Collector hint(String message) {
            return hint(StackWalker.getInstance(WALKER_OPTIONS).getCallerClass(), message);
        }

        /**
         * Add a hint message to the list of messages.
         *
         * @param source  source of the hint
         * @param message message of the hint
         * @return updated collector
         */
        public Collector hint(Object source, String message) {
            return message(source, message, Severity.HINT);
        }

        /**
         * Process the messages collected into an {@link Errors} instance.
         * Clear this collector (e.g. it can be used to collect new messages).
         *
         * @return new {@link Errors} instance built with messages collected by this collecto
         */
        public Errors collect() {
            Errors errors = new Errors(this);
            clear();
            return errors;
        }

        /**
         * Clear this instance by discarding all {@link ErrorMessage}s collected and re-setting status.
         *
         * @return updated collector
         */
        public Collector clear() {
            this.errors.clear();
            this.hasHint = false;
            this.hasFatal = false;
            this.hasWarning = false;

            return this;
        }

        /**
         * A helper method to check if this collector already has a fatal message.
         *
         * @return true if this collector contains at least one fatal message
         */
        public boolean hasFatal() {
            return hasFatal;
        }
    }

    /**
     * Exception used by {@link Errors#checkValid()} thrown in case there are fatal messages.
     * This exception provides access to all the messages of {@link Errors} that created it.
     */
    public static final class ErrorMessagesException extends RuntimeException {

        private final List<ErrorMessage> messages;

        private ErrorMessagesException(final List<ErrorMessage> messages) {
            super(messages.toString());

            this.messages = messages;
        }

        /**
         * Get encountered error messages of all types (hint, warning, fatal).
         *
         * @return encountered error messages.
         */
        public List<ErrorMessage> getMessages() {
            return List.copyOf(messages);
        }
    }

    /**
     * Error message with a severity and a source.
     * Used from {@link Errors}.
     */
    public static class ErrorMessage {

        private final Object source;
        private final String message;
        private final Severity severity;

        // used from unit tests, keep package local
        ErrorMessage(final Object source, final String message, Severity severity) {
            this.source = source;
            this.message = message;
            this.severity = severity;
        }

        /**
         * Get {@link Severity}.
         *
         * @return severity of current {@code ErrorMessage}.
         */
        public Severity getSeverity() {
            return severity;
        }

        /**
         * Human-readable description of the issue.
         *
         * @return message describing the issue.
         */
        public String getMessage() {
            return message;
        }

        /**
         * The issue source.
         * <p>
         * Identifies the object where the issue was found.
         *
         * @return source of the issue.
         */
        public Object getSource() {
            return source;
        }

        @Override
        public String toString() {
            return severity + ": " + message + " at " + source;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if ((o == null) || (getClass() != o.getClass())) {
                return false;
            }
            ErrorMessage that = (ErrorMessage) o;
            return Objects.equals(source, that.source)
                    && Objects.equals(message, that.message)
                    && (severity == that.severity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, message, severity);
        }
    }
}
