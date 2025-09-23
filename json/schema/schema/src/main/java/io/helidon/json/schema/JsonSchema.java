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

package io.helidon.json.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JSON schema is used to describe required JSON format.
 * This class contains available JSON schema annotations.
 */
public final class JsonSchema {

    private JsonSchema() {
    }

    /**
     * Marker annotation for a codegen to generate JSON schema from the annotated type.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    public @interface Schema {
    }

    /**
     * The base URI for resolving relative references.
     * This will be added only to a root JSON schema.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    public @interface Id {

        /**
         * The value of the base URI for resolving relative references.
         *
         * @return base URI value
         */
        java.lang.String value();
    }

    /**
     * Title of the JSON schema.
     */
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Title {

        /**
         * JSON schema title.
         *
         * @return schema title
         */
        java.lang.String value();
    }

    /**
     * Description of the JSON schema.
     */
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Description {

        /**
         * JSON schema description.
         *
         * @return schema description
         */
        java.lang.String value();
    }

    /**
     * Marker annotation for a required JSON properties.
     * Applicable to oll JSON properties.
     */
    @Target({ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Required {
    }

    /**
     * Whether to avoid deeper inspection of the JSON property type.
     */
    @Target({ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface DoNotInspect {
    }

    /**
     * Whether the class property should be ignored and not included into the schema.
     */
    @Target({ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Ignore {
    }

    /**
     * How is the JSON property named in the JSON schema.
     */
    @Target({ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface PropertyName {

        /**
         * JSON schema property name.
         *
         * @return selected property name
         */
        java.lang.String value();
    }

    /**
     * Integer related schema annotations.
     */
    public static final class Integer {
        private Integer() {
        }

        /**
         * All integer values should be multiples of the given number.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MultipleOf {

            /**
             * Value which integer values should be multiplication of.
             *
             * @return long value of the required multiplication number
             */
            long value();
        }

        /**
         * Minimum value of the integer number.
         * Explanation: validated number {@literal >=} minimum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface Minimum {

            /**
             * Integer number minimum.
             *
             * @return selected minimum value
             */
            long value();
        }

        /**
         * Maximum value of the integer number.
         * Explanation: validated number {@literal <=} maximum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface Maximum {

            /**
             * Integer number maximum.
             *
             * @return selected maximum value
             */
            long value();
        }

        /**
         * Exclusive maximum value of the integer number.
         * Explanation: validated number {@literal <} exclusive maximum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface ExclusiveMaximum {

            /**
             * Integer number exclusive maximum.
             *
             * @return selected exclusive maximum value
             */
            long value();
        }

        /**
         * Exclusive minimum value of the integer number.
         * Explanation: validated number {@literal >} exclusive minimum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface ExclusiveMinimum {

            /**
             * Integer number exclusive minimum.
             *
             * @return selected exclusive minimum value
             */
            long value();
        }
    }

    /**
     * Number related schema annotations.
     */
    public static final class Number {

        private Number() {
        }

        /**
         * All number values should be multiples of the given number.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MultipleOf {

            /**
             * Value which number values should be multiplication of.
             *
             * @return selected value of the required multiplication number
             */
            double value();
        }

        /**
         * Minimum value of the validated number.
         * Explanation: validated number {@literal >=} minimum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface Minimum {

            /**
             * Minimum number value.
             *
             * @return selected minimum value
             */
            double value();
        }

        /**
         * Maximum value of the validated number.
         * Explanation: validated number {@literal <=} maximum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface Maximum {

            /**
             * Maximum number value.
             *
             * @return selected maximum value
             */
            double value();
        }

        /**
         * Exclusive maximum value of the number.
         * Explanation: validated number {@literal <} exclusive maximum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface ExclusiveMaximum {

            /**
             * Number exclusive maximum.
             *
             * @return selected exclusive maximum value
             */
            double value();
        }

        /**
         * Exclusive minimum value of the number.
         * Explanation: validated number {@literal >} exclusive minimum.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface ExclusiveMinimum {

            /**
             * Validated number exclusive minimum.
             *
             * @return selected exclusive minimum value
             */
            double value();
        }
    }

    /**
     * String related schema annotations.
     */
    public static final class String {

        private String() {
        }

        /**
         * Minimal length of the String value.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MinLength {

            /**
             * Minimal String value length.
             *
             * @return selected minimal String value length
             */
            long value();
        }

        /**
         * Maximal length of the String value.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MaxLength {

            /**
             * Maximal String value length.
             *
             * @return selected maximal String value length
             */
            long value();
        }

        /**
         * Regexp pattern the String value has to follow.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface Pattern {

            /**
             * Regexp pattern of the String value.
             *
             * @return selected regexp pattern
             */
            java.lang.String value();
        }

    }

    /**
     * Object related schema annotations.
     */
    public static final class Object {

        private Object() {
        }

        /**
         * Minimal number of the object properties in the JSON.
         */
        @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MinProperties {

            /**
             * Minimal number of the properties.
             *
             * @return selected minimal number of the properties
             */
            int value();
        }

        /**
         * Maximal number of the object properties in the JSON.
         */
        @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MaxProperties {

            /**
             * Maximal number of the properties.
             *
             * @return selected maximal number of the properties
             */
            int value();
        }

        /**
         * Whether to allow additional properties.
         * When disabled, only explicitly mentioned properties in the {@code properties} JSON property will be allowed.
         */
        @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface AdditionalProperties {

            /**
             * Whether to allow all JSON properties.
             * The default value is {@code true}.
             *
             * @return whether to allow all JSON properties
             */
            boolean value() default true;
        }

    }

    /**
     * Array related schema annotations.
     */
    public static final class Array {

        private Array() {
        }

        /**
         * Maximum number of the items the array can have.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MaxItems {

            /**
             * Maximum number of the items.
             *
             * @return selected maximum number of the items
             */
            int value();
        }

        /**
         * Minimum number of the items the array can have.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface MinItems {

            /**
             * Minimum number of the items.
             *
             * @return selected minimum number of the items
             */
            int value();
        }

        /**
         * Whether the array must contain only the unique items.
         */
        @Target({ElementType.METHOD, ElementType.FIELD})
        @Retention(RetentionPolicy.CLASS)
        public @interface UniqueItems {
            /**
             * Whether the array must contain only the unique items.
             * Default value is {@code true}.
             *
             * @return only unique values
             */
            boolean value() default true;
        }
    }

}
