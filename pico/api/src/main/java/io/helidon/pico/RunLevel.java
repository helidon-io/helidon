/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Indicates the desired startup sequence for a service class.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
@Inherited
public @interface RunLevel {

    /**
     * Represents an eager singleton.
     */
    int STARTUP = 0;

    /**
     * Anything > 0 is left to the underlying provider implementation's discretion for meaning; this is just a default for something
     * that is deemed "other than startup".
     */
    int NORMAL = 100;

    /**
     * The service ranking applied when not declared explicitly.
     *
     * @return the startup int value, defaulting to {@link #NORMAL}
     */
    int value() default NORMAL;

}
