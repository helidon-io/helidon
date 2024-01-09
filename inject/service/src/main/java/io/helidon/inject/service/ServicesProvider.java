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

package io.helidon.inject.service;

import java.util.List;

import io.helidon.common.types.TypeName;

/**
 * Provides an ability to create more than one service instance from a single service definition.
 * This is useful when the cardinality can only be determined at runtime.
 *
 * @param <T> type of the provided services
 */
public interface ServicesProvider<T> {
    /**
     * Type name of this interface.
     */
    TypeName TYPE_NAME = TypeName.create(ServicesProvider.class);

    /**
     * List of service suppliers.
     * Each instance may have a different set of qualifiers.
     * <p>
     * The following is inherited from this provider:
     * <ul>
     *     <li>Set of contracts, except for {@link io.helidon.inject.service.ServicesProvider}</li>
     *     <li>Scope</li>
     *     <li>Run level</li>
     *     <li>Weight</li>
     * </ul>
     *
     * @return qualified suppliers of service instances
     */
    List<QualifiedInstance<T>> services();
}
