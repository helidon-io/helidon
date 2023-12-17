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

package io.helidon.inject.spi;

import io.helidon.inject.Activator;
import io.helidon.inject.Services;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * A {@link java.util.ServiceLoader} provider interface that allows extensibility of the activator created based
 * on the service descriptor.
 * The {@link #id()} is matched with {@link io.helidon.inject.service.ServiceDescriptor#runtimeId()} and the provider with
 * highest {@link io.helidon.common.Weight} for that id would be used to prepare an activator for the service descriptor.
 */
public interface ActivatorProvider {
    /**
     * Id to match with {@link io.helidon.inject.service.ServiceDescriptor#runtimeId()}.
     *
     * @return id this provider provides
     */
    String id();

    /**
     * Create a service activator for a service descriptor.
     *
     * @param services   service registry (the service registry is being initialized when activators are created!)
     * @param descriptor descriptor to create activator for
     * @param <T>        type of the provided service
     * @return a new activator
     */
    <T> Activator<T> activator(Services services, ServiceDescriptor<T> descriptor);
}
