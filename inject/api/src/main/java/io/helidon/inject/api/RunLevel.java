/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Indicates the desired startup sequence for a service class. This is not used internally by Injection, but is available as a
 * convenience to the caller in support for a specific startup sequence for service activations.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
public @interface RunLevel {

    /**
     * Represents an eager singleton that should be started at "startup". Note, however, that callers control the actual
     * activation for these services, not the framework itself, as shown below:
     * <pre>
     * {@code
     * List<ServiceProvider<Object>> startupServices = services
     *               .lookup(ServiceInfoCriteria.builder().runLevel(RunLevel.STARTUP).build());
     *       startupServices.stream().forEach(ServiceProvider::get);
     * }
     * </pre>
     */
    int STARTUP = 10;

    /**
     * Anything > 0 is left to the underlying provider implementation's discretion for meaning; this is just a default for
     * something that is deemed "other than startup".
     */
    int NORMAL = 100;

    /**
     * The service ranking applied when not declared explicitly.
     *
     * @return the startup int value, defaulting to {@link #NORMAL}
     */
    int value() default NORMAL;

}
