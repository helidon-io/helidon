package io.helidon.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Container class fpr various constraint annotations.
 */
public final class Constraints {
    private Constraints() {
    }

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
             * @return flags to add when creatign the pattern
             */
            Flag[] flags() default {};

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
            java.lang.String value();
        }

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
            java.lang.String value();
        }

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
             * Maximal scale of the number.
             *
             * @return maximal scale, not validated by default
             */
            int scale() default -1;

            /**
             * Maximal precision of the number.
             *
             * @return maximal precision, not validated by default
             */
            int precision() default -1;
        }

    }

    public static final class Integer {
        private Integer() {
        }

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
            int value();
        }

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
            int value();
        }
    }

    public static final class Long {
        private Long() {
        }

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
            long value();
        }

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
            long value();
        }
    }

    public static final class Boolean {
        private Boolean() {
        }

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

    // all calendar validators must be implemented
    public static final class Calendar {
        private Calendar() {
        }

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

    // all collection validators must be implemented
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
