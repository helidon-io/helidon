/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;

/**
 * {@link io.helidon.common.types.TypeName} that are commonly needed at runtime.
 *
 * @see io.helidon.common.types.TypeNames
 */
public final class InjectTypes {
    /**
     * Helidon {@link io.helidon.inject.service.Injection.Singleton}.
     */
    public static final TypeName SINGLETON = TypeName.create(Injection.Singleton.class);
    /**
     * Helidon {@link io.helidon.inject.service.Injection.Named}.
     */
    public static final TypeName NAMED = TypeName.create(Injection.Named.class);

    private InjectTypes() {
    }
}
