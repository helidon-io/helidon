/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single configuration/value option.
 */
@Target(ElementType.METHOD)
@Inherited
@Retention(RetentionPolicy.SOURCE)
public @interface ConfiguredOption {

    boolean DEFAULT_REQUIRED = false;

    String DEFAULT_VALUE = "";
//    char[] EMPTY_CHARS = {};

    /**
     * The alias in the config system.
     *
     * @return the alias, defaulting to the same name as the getter this is on.
     */
    String key() default DEFAULT_VALUE;

    /**
     * Description of the configuration option.
     * The javadoc of the builder method is used by default. If this annotation exists
     * on a class, description is mandatory.
     *
     * @return description of the configuration option
     */
    String description() default DEFAULT_VALUE;

    /**
     * Whether this option is truly required (e.g. the option must be present in configuration, otherwise
     * the component would fail). This MUST NOT be configured together with default value, as that would
     * make this option optional (as a default value exists).
     *
     * @return {@code true} for required option, {@code false} for options that are optional.
     */
    boolean required() default DEFAULT_REQUIRED;

    /**
     * Default value of this option if not configured explicitly.
     *
     * @return default value
     */
    String value() default DEFAULT_VALUE;

//    /**
//     * Default value of this option if not configured explicitly.
//     * This serves as an alternative to {@link io.helidon.pico.builder.api.DefaultValue}.
//     *
//     * @return default value
//     */
//    int defaultIntValue() default 0;
//
//    /**
//     * Default value of this option if not configured explicitly.
//     * This serves as an alternative to {@link io.helidon.pico.builder.api.DefaultValue}.
//     *
//     * @return default value
//     */
//    boolean defaultBooleanValue() default false;
//
//    /**
//     * Default value of this option if not configured explicitly.
//     * This serves as an alternative to {@link io.helidon.pico.builder.api.DefaultValue}.
//     *
//     * @return default value
//     */
//    char[] defaultCharArrayValue() default {};
//
    /**
     * For options that have a predefined set of allowed values.
     *
     * @return allowed values
     */
    String[] allowedValues() default {};

//    /**
//     * Configure to {@code true} if this option is deprecated.
//     *
//     * @return whether this configured option is deprecated
//     */
//    boolean deprecated() default false;

}
