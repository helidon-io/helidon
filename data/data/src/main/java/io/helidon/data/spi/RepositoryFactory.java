/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.spi;

import java.util.function.Function;

/**
 * Each repository implementation provides its own factory capable of creating repository instances.
 * <p>
 * Instance is obtained through {@link DataSupport#repositoryFactory()}.
 */
public interface RepositoryFactory {
    /**
     * Create a new repository instance.
     *
     * @param creator function that creates an instance based on an executor (that is provider specific)
     * @param <T>     type of the repository
     * @param <E>     type the repository expects to be instantiated (specific to each data support)
     * @return result of the creator
     */
    <E, T> T create(Function<E, T> creator);
}
