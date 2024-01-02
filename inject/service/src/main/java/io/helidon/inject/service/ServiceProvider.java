/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * A service can implement this type to act as a provider of instances of type {@code T}.
 * <p>
 * Responsibility to create and manage instance is fully on this provider instance.
 * The same contract is valid for {@link java.util.function.Supplier}, this interface exists to
 * be more explicit that this is a supplier of services, not a general supplier.
 *
 * @param <T> type of the provided service
 */
@SuppressWarnings("checkstyle:InterfaceIsType")
public interface ServiceProvider<T> extends Supplier<T> {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(ServiceProvider.class);
}
