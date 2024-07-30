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
 * A config driven service is based on a prototype that is configured from the root of configuration
 * (see {@code Configured} in {@code helidon-config-metadata}).
 * <p>
 * The annotation is placed on the service implementation (not contract, as we need to understand which type to
 * instantiate), and the prototype is expected to be one of the constructor parameters (annotated with {@code @Inject}).
 * In case the configured prototype is repeatable, each instance will be named according to the name specified in configuration
 * either through {@code name} property, or the config node name.
 * <p>
 * Example:
 * <pre>
 * &#064;ConfigDriven(value = ServerConfig.class, activateByDefault  = true, atLeastOne = true)
 * class ServerImpl {
 *   &#064;Inject
 *   ServerImpl(ServerConfig sc) {
 *   }
 * }
 * </pre>
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(java.lang.annotation.ElementType.TYPE)
@Qualifier
public @interface ConfigDriven {
    /**
     * The prototype class that drives this config driven.
     *
     * @return the prototype that is configured
     */
    Class<?> value();

    /**
     * Determines whether this instance will be activated if created.
     * The default value is {@code false}.
     *
     * @return true if the presence of the config bean has an activation affect
     */
    boolean activateByDefault() default false;
}
