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

import io.helidon.common.types.TypeName;

import jakarta.inject.Named;

/**
 * Commonly used {@link Qualifier} types.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public final class CommonQualifiers {

    /**
     * Represents a {@link jakarta.inject.Named} type name with no value.
     */
    public static final TypeName NAMED = TypeName.create(Named.class);

    /**
     * Represents a wildcard (i.e., matches anything).
     */
    public static final String WILDCARD = "*";

    /**
     * Represents a wildcard {@link #NAMED} qualifier.
     */
    public static final Qualifier WILDCARD_NAMED = Qualifier.createNamed(WILDCARD);

    private CommonQualifiers() {
    }

}
