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

package io.helidon.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A container class for default values related types for Helidon declarative.
 */
public final class Default {
    private Default() {
    }

    /**
     * A default value specified as a string.
     * <p>
     * Depending on the usage, this may be mapped to other types as needed.
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Value {
        /**
         * Default value(s) for this element.
         *
         * @return default value as a string, or an array of strings, if the element is a list, set, or an array
         */
        String[] value();
    }

    /**
     * A default value specified as an integer.
     * This can only be used on element of the correct type.
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Int {
        /**
         * Default value(s) for this element.
         *
         * @return default value as an integer, or an array of integers, if the element is a list, set, or an array
         */
        int[] value();
    }

    /**
     * A default value specified as a long.
     * This can only be used on element of the correct type.
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Long {
        /**
         * Default value(s) for this element.
         *
         * @return default value as a long, or an array of longs, if the element is a list, set, or an array
         */
        long[] value();
    }

    /**
     * A default value specified as a double.
     * This can only be used on element of the correct type.
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Double {
        /**
         * Default value(s) for this element.
         *
         * @return default value as a double, or an array of doubles, if the element is a list, set, or an array
         */
        double[] value();
    }

    /**
     * A default value specified as a boolean.
     * This can only be used on element of the correct type.
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Boolean {
        /**
         * Default value(s) for this element.
         *
         * @return default value as a boolean, or an array of booleans, if the element is a list, set, or an array
         */
        boolean[] value();
    }
}
