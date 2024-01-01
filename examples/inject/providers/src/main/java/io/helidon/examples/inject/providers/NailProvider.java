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

package io.helidon.examples.inject.providers;

import java.util.function.Supplier;

import io.helidon.inject.service.Injection;

/**
 * Showcases dependent scope-creating a nail for caller's demand for a {@link Nail} to be provided.
 * All {@code Provider}s control the scope of the service instances they provide.
 *
 * @see BladeProvider
 */
@Injection.Singleton
class NailProvider implements Supplier<Nail> {

    /**
     * Creates a new nail every its called.
     *
     * @return a new nail instance
     */
    @Override
    public Nail get() {
        return new StandardNail();
    }

}
