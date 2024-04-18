/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * This configured prototype should be acting as a config bean. This means that if appropriate configuration
 * exists (must be a root configured type with a defined prefix), an instance will be created from that configuration.
 * Additional setup is possible to ensure an instance even if not present in config, and to create default (unnamed) instance.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(java.lang.annotation.ElementType.TYPE)
@Qualifier
public @interface ConfigBean {
    /**
     * An instance of this bean will be created if there are no instances discovered by the configuration provider(s) post
     * startup, and will use all default values annotated using {@code ConfiguredOptions} from the bean interface methods.
     * <p>
     * The default value is {@code false}.
     *
     * @return the default config bean instance using defaults
     */
    boolean atLeastOne() default false;

    /**
     * Determines whether there can be more than one bean instance of this type.
     * <p>
     * If false then only 0..1 behavior will be permissible for active beans in the config registry. If true then {@code > 1}
     * instances will be permitted.
     * <p>
     * Note: this attribute is dynamic in nature, and therefore cannot be validated at compile time. All violations found to this
     * policy will be observed during <i>Services</i> activation.
     * <p>
     * The default value is {@code false}.
     *
     * @return true if repeatable
     */
    boolean repeatable() default false;

    /**
     * There will always be an instance created with defaults, that will not be named.
     * <p>
     * The default value is {@code false}.
     *
     * @return use the default config instance
     */
    boolean wantDefault() default false;
}
