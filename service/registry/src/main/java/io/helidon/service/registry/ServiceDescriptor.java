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

package io.helidon.service.registry;

/**
 * A descriptor of a service. In addition to providing service metadata, this also allows instantiation
 * of the service instance, with dependent services as parameters.
 *
 * @param <T> type of the described service
 */
@SuppressWarnings("removal")
public interface ServiceDescriptor<T> extends ServiceInfo, GeneratedService.Descriptor<T> {
    @Override
    default Object instantiate(DependencyContext ctx) {
        return GeneratedService.Descriptor.super.instantiate(ctx);
    }

    @Override
    default void postConstruct(T instance) {
        GeneratedService.Descriptor.super.postConstruct(instance);
    }

    @Override
    default void preDestroy(T instance) {
        GeneratedService.Descriptor.super.preDestroy(instance);
    }
}
