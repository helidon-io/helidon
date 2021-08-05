/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.config.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single configuration option.
 * Must be used together with {@link io.helidon.config.metadata.Configured}.
 * Usually this should target a builder method that configures appropriate option.
 * If defined on a static method, it must be a {code static T create(X)} method, where
 * X is either {@code io.helidon.config.Config} or a single option.
 * <p>
 * There may be multiple {@code create(X)} methods defined on a single interface/class, each
 * of them annotated with a different list of options. In such a case the grouping
 * should be used as defined on these methods. If a method {@code create(Config)} exists,
 * it should use the same groups (e.g. if one method defines URI and Proxy, and another
 * method defines a Path, the {@code create(Config)} method can accept either (URI, proxy host, proxy port)
 * or (path).
 * <p>
 * By default, the name of the method is considered to be the configuration option used.
 * If method contains camel case, it will be changed to lower case hyphenated (this describes
 * the implementation of Helidon specific annotation processor and expectations).
 * <p>
 * Example - method {@code public Builder listenAddress(String)} would be configured
 * by key {@code listen-address}.
 */
@Target(ElementType.METHOD)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ConfiguredOptions.class)
public @interface ConfiguredOption {
    /**
     * A string meaning that a value is not specified (to allow empty String as a meaningful value).
     */
    String UNCONFIGURED = "io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED";

    /**
     * The key of the configuration option as used in config.
     *
     * @return config key
     */
    String value() default "";

    /**
     * The type of the configuration option.
     * By default it is the type of the first parameter. If this annotation
     * exists on a class, type defaults to {@link java.lang.String}.
     *
     * @return type of the configuration option
     */
    Class<?> type() default ConfiguredOption.class;

    /**
     * Description of the configuration option.
     * By default javadoc of the builder method is used. If this annotation exists
     * on a class, description is mandatory.
     *
     * @return description of the configuration option
     */
    String description() default "";

    /**
     * Whether this option is truly required (e.g. the option must be present in configuration, otherwise
     * the component would fail). This MUST NOT be configured together with default value, as that would
     * make this option optional (as a default value exists).
     *
     * @return {@code true} for required option, {@code false} for options that are optional.
     */
    boolean required() default false;

    /**
     * Default value of this option if not configured explicitly.w
     * @return default value
     */
    String defaultValue() default UNCONFIGURED;

    /**
     * Set to {@code true} for experimental configuration.
     *
     * @return whether this option is experimental
     */
    boolean experimental() default false;

    /**
     * Can be used to mark a builder method (or an option) as a list.
     * Lists will be auto-detected if the parameter is an actual {@link java.util.List} or {@link java.util.Set}.
     *
     * @return whether this options is expected to be a list of values
     */
    boolean list() default false;

    /**
     * For options that have a predefined set of allowed values.
     *
     * @return allowed values
     */
    ConfiguredValue[] allowedValues() default {};
}
