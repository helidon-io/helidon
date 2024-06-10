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

package io.helidon.inject.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.inject.Named;
import jakarta.inject.Qualifier;

/**
 * This annotation is effectively the same as {@link jakarta.inject.Named} where the {@link Named#value()} is a {@link Class}
 * name instead of a {@link String}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ClassNamed {

    /**
     * The class used will function as the name.
     *
     * @return the class
     */
    Class<?> value();

}
