/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.helidon.service.registry.Interception;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Validation annotations and related types.
 * <p>
 * Annotations:
 * <ul>
 *     <li>{@link io.helidon.validation.Validation.Validated} - triggers code-generation of validation code for a type</li>
 *     <li>{@link io.helidon.validation.Validation.Valid} - triggers validation of a type using the generated code</li>
 *     <li>{@link io.helidon.validation.Validation.Constraint} - marks an annotation as a constraint annotation,
 *              each such annotation triggers validation of the value against the constraint</li>
 *      <li>The rest: built-in constraint annotations</li>
 * </ul>
 */
public final class Validation {
    private Validation() {
    }

    /**
     * Definition of a constraint.
     * A validator is discovered from the service registry - the first instance that has
     * {@link io.helidon.validation.spi.ConstraintValidator} contract, and is named as the
     * fully qualified name of the constraint annotation.
     */
    @Documented
    @Target(ANNOTATION_TYPE)
    @Retention(CLASS)
    @Interception.Intercepted
    public @interface Constraint {
    }

    /**
     * This type will contain validations on getters (or record components) that cannot be intercepted.
     * Such a type will have a generated validator that will be used by interceptors.
     * <p>
     * The generated type will be a {@link io.helidon.validation.spi.TypeValidator}
     * named with the fully qualified class name of the annotated type.
     */
    @Documented
    @Target(TYPE)
    public @interface Validated {
    }

    /**
     * Mark an element as validated even when no explicit constraints are added on it to validate
     * the nested object structure.
     * <p>
     * Each object must be annotated with {@link io.helidon.validation.Validation.Validated}, as otherwise
     * we cannot know what to do (Helidon only supports build-time generated validations, we do not use
     * reflection to analyze types).
     */
    @Retention(CLASS)
    @Target({METHOD, FIELD, CONSTRUCTOR, PARAMETER, TYPE_USE})
    @Interception.Intercepted
    public @interface Valid {
        /**
         * Can be set to {@code false} to explicitly disable all validations on this element.
         *
         * @return whether to validate an element (deep validation)
         */
        boolean value() default true;
    }

    /**
     * Value must not be {@code null}.
     */
    @Target({
            ElementType.METHOD, // return type
            ElementType.PARAMETER,  // parameter of a method
            ElementType.ANNOTATION_TYPE, // annotation to inherit
            ElementType.TYPE_USE, // Map<Key, @NotNull Value>
            ElementType.RECORD_COMPONENT // components of a record
    })
    @Validation.Constraint
    public @interface NotNull {
        /**
         * Message to return instead of the default one of the validator.
         * The message can be a string format, where the first (and only) parameter will be the actual value
         * that is being validated.
         *
         * @return message to describe the constraint validation error
         */
        java.lang.String message() default "";
    }

    /**
     * Value must be {@code null}.
     */
    @Target({
            ElementType.METHOD, // return type
            ElementType.PARAMETER,  // parameter of a method
            ElementType.ANNOTATION_TYPE, // annotation to inherit
            ElementType.TYPE_USE, // Map<Key, @NotNull Value>
            ElementType.RECORD_COMPONENT // components of a record
    })
    @Validation.Constraint
    public @interface Null {
        /**
         * Message to return instead of the default one of the validator.
         * The message can be a string format, where the first (and only) parameter will be the actual value
         * that is being validated.
         *
         * @return message to describe the constraint validation error
         */
        java.lang.String message() default "";
    }

    /**
     * {@link java.lang.String} and
     * {@link java.lang.CharSequence} constraints.
     */
    public static final class String {
        private String() {
        }

        /**
         * Validate that the annotated char sequence is a valid e-mail address.
         * If a custom regular expression is needed, use {@link Pattern} instead.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Email {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value must not be blank.
         * A blank value is either empty or contains only whitespace characters.
         *
         * @see java.lang.String#isBlank()
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface NotBlank {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value must not be empty.
         *
         * @see java.lang.String#isEmpty()
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface NotEmpty {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value's length must be between {@link #min()} and {@link #value()} characters (inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Length {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Minimal length of the char sequence. Defaults to zero.
             *
             * @return min length
             */
            int min() default 0;

            /**
             * Maximal length of the char sequence. Defaults to maximal integer value.
             *
             * @return max length
             */
            int value() default java.lang.Integer.MAX_VALUE;
        }

        /**
         * The value must match the given regular expression.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Pattern {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * The regular expression.
             *
             * @return regular expression to match
             */
            java.lang.String value();

            /**
             * Pattern flags to use.
             *
             * @return flags to add when creating the pattern
             */
            Flag[] flags() default {};

            /**
             * Enumeration of regular expression flags, that maps to the correct constants on
             * {@link java.util.regex.Pattern}, such as {@link java.util.regex.Pattern#CASE_INSENSITIVE}.
             */
            enum Flag {
                /**
                 * Enables Unix lines mode.
                 *
                 * @see java.util.regex.Pattern#UNIX_LINES
                 */
                UNIX_LINES(java.util.regex.Pattern.UNIX_LINES),

                /**
                 * Enables case-insensitive matching.
                 *
                 * @see java.util.regex.Pattern#CASE_INSENSITIVE
                 */
                CASE_INSENSITIVE(java.util.regex.Pattern.CASE_INSENSITIVE),

                /**
                 * Permits whitespace and comments in pattern.
                 *
                 * @see java.util.regex.Pattern#COMMENTS
                 */
                COMMENTS(java.util.regex.Pattern.COMMENTS),

                /**
                 * Enables multiline mode.
                 *
                 * @see java.util.regex.Pattern#MULTILINE
                 */
                MULTILINE(java.util.regex.Pattern.MULTILINE),

                /**
                 * Enables dotall mode.
                 *
                 * @see java.util.regex.Pattern#DOTALL
                 */
                DOTALL(java.util.regex.Pattern.DOTALL),

                /**
                 * Enables Unicode-aware case folding.
                 *
                 * @see java.util.regex.Pattern#UNICODE_CASE
                 */
                UNICODE_CASE(java.util.regex.Pattern.UNICODE_CASE),

                /**
                 * Enables canonical equivalence.
                 *
                 * @see java.util.regex.Pattern#CANON_EQ
                 */
                CANON_EQ(java.util.regex.Pattern.CANON_EQ);

                private final int value;

                Flag(int value) {
                    this.value = value;
                }

                /**
                 * The regular expression flags to use when creating the {@link java.util.regex.Pattern}.
                 *
                 * @return flag value as defined in {@link java.util.regex.Pattern}
                 */
                public int value() {
                    return value;
                }
            }
        }
    }

    /**
     * Validators for any type of number, including {@link java.math.BigDecimal}, {@link java.math.BigInteger},
     * {@link java.lang.String}, {@link java.lang.CharSequence} etc.
     */
    public static final class Number {
        private Number() {
        }

        /**
         * The value must be negative.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Negative {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value must be negative or zero.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface NegativeOrZero {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value must be positive.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Positive {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value must be positive or zero.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         * <p>
         * A value of negative zero (possible for long data type) is considered a zero.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface PositiveOrZero {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value must be the specified {@link #value()} or higher.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Min {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Minimal value. This value will be converted to a {@link java.math.BigDecimal}, so it must match its rules.
             *
             * @return the minimal value
             */
            java.lang.String value();
        }

        /**
         * The value must be the specified {@link #value()} or lower.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Max {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Maximal value. This value will be converted to a {@link java.math.BigDecimal}, so it must match its rules.
             *
             * @return the maximal value
             */
            java.lang.String value();
        }

        /**
         * The value must have the at most he defined number of {@link #integer()} and {@link #fraction()} digits.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Digits {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Maximal number of digits to the left of the decimal mark.
             * If this is set to 2, the maximal number supported is 99.
             *
             * @return maximal number of integer digits, not validated by default
             */
            int integer() default -1;

            /**
             * Maximal number of digits to the right of the decimal mark.
             * If this is set to 2, the values can be {@code 0.01}, but not {@code 0.001}.
             *
             * @return maximal number of fraction digits, not validated by default
             */
            int fraction() default -1;
        }

    }

    /**
     * Integer constraints, to allow use of int constants with values.
     * These are convenience constraints and could be replaced with constraints in
     * {@link io.helidon.validation.Validation.Number}.
     */
    public static final class Integer {
        private Integer() {
        }

        /**
         * The value must be the specified {@link #value()} or higher.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Min {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Minimal value.
             *
             * @return minimal value
             */
            int value();
        }

        /**
         * The value must be the specified {@link #value()} or lower.
         * <p>
         * Bytes are considered unsigned values (always between 0 and 255 inclusive).
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Max {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Maximal value.
             *
             * @return maximal value
             */
            int value();
        }
    }

    /**
     * Integer constraints, to allow use of long constants with values.
     * These are convenience constraints and could be replaced with constraints in
     * {@link io.helidon.validation.Validation.Number}.
     * <p>
     * NOTE: long constraints are only allowed on long values
     * (or boxed {@link io.helidon.validation.Validation.Long}) and will
     * fail on any other type.
     */
    public static final class Long {
        private Long() {
        }

        /**
         * The value must be the specified {@link #value()} or higher.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Min {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Minimal value.
             *
             * @return min value
             */
            long value();
        }

        /**
         * The value must be the specified {@link #value()} or lower.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Max {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Maximal value.
             *
             * @return max value
             */
            long value();
        }
    }

    /**
     * Boolean constraints.
     * <p>
     * NOTE: only valid for boolean (and boxed {@link java.lang.Boolean}) types.
     */
    public static final class Boolean {
        private Boolean() {
        }

        /**
         * The value must be true.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface True {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The value must be false.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface False {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }
    }

    /**
     * Calendar constraints.
     * <p>
     * All constraints in this class support the following types:
     * <ul>
     *     <li>{@code java.util.Date}</li>
     *     <li>{@code java.util.Calendar}</li>
     *     <li>{@code java.time.Instant}</li>
     *     <li>{@code java.time.LocalDate}</li>
     *     <li>{@code java.time.LocalDateTime}</li>
     *     <li>{@code java.time.LocalTime}</li>
     *     <li>{@code java.time.MonthDay}</li>
     *     <li>{@code java.time.OffsetDateTime}</li>
     *     <li>{@code java.time.OffsetTime}</li>
     *     <li>{@code java.time.Year}</li>
     *     <li>{@code java.time.YearMonth}</li>
     *     <li>{@code java.time.ZonedDateTime}</li>
     *     <li>{@code java.time.chrono.HijrahDate}</li>
     *     <li>{@code java.time.chrono.JapaneseDate}</li>
     *     <li>{@code java.time.chrono.MinguoDate}</li>
     *     <li>{@code java.time.chrono.ThaiBuddhistDate}</li>
     * </ul>
     */
    public static final class Calendar {
        private Calendar() {
        }

        /**
         * The annotated element must be in the future.
         * <p>
         * The definition of future depends on annotated type - for example, for a {@link java.time.Year},
         * future is next year (even in January), but for {@link java.time.Instant}, future is already the next millisecond.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Future {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The annotated element must be in the future or present.
         * <p>
         * The definition of future depends on annotated type - for example, for a {@link java.time.Year},
         * future is next year (even in January), but for {@link java.time.Instant}, future is already the next millisecond.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface FutureOrPresent {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The annotated element must be in the past.
         * <p>
         * The definition of past depends on annotated type - for example, for a {@link java.time.Year},
         * past is last year (even in December), but for {@link java.time.Instant}, past was already the previous millisecond.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Past {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }

        /**
         * The annotated element must be in the past or present.
         * <p>
         * The definition of past depends on annotated type - for example, for a {@link java.time.Year},
         * past is last year (even in December), but for {@link java.time.Instant}, past was already the previous millisecond.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface PastOrPresent {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";
        }
    }

    /**
     * Collection constraints.
     */
    public static final class Collection {
        private Collection() {
        }

        /**
         * Supported for {@link java.util.Collection}, {@link java.util.Map}, and arrays.
         */
        @Target({
                ElementType.METHOD, // return type
                ElementType.PARAMETER,  // parameter of a method
                ElementType.ANNOTATION_TYPE, // annotation to inherit
                ElementType.TYPE_USE, // Map<Key, @NotNull Value>
                ElementType.RECORD_COMPONENT // components of a record
        })
        @Validation.Constraint
        public @interface Size {
            /**
             * Message to return instead of the default one of the validator.
             * The message can be a string format, where the first (and only) parameter will be the actual value
             * that is being validated.
             *
             * @return message to describe the constraint validation error
             */
            java.lang.String message() default "";

            /**
             * Minimal size of the collection. Defaults to zero.
             *
             * @return min length
             */
            int min() default 0;

            /**
             * Maximal size of the collection. Defaults to maximal integer value.
             *
             * @return max length
             */
            int value() default java.lang.Integer.MAX_VALUE;
        }
    }
}
