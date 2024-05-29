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

package io.helidon.service.codegen;

import io.helidon.common.types.TypeName;

/**
 * Types used in code generation of Helidon Service.
 */
public final class ServiceCodegenTypes {
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Provider}.
     */
    public static final TypeName SERVICE_ANNOTATION_PROVIDER = TypeName.create("io.helidon.service.registry.Service.Provider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Contract}.
     */
    public static final TypeName SERVICE_ANNOTATION_CONTRACT = TypeName.create("io.helidon.service.registry.Service.Contract");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.ExternalContracts}.
     */
    public static final TypeName SERVICE_ANNOTATION_EXTERNAL_CONTRACTS = TypeName.create("io.helidon.service.registry.Service"
                                                                                                 + ".ExternalContracts");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Descriptor}.
     */
    public static final TypeName SERVICE_ANNOTATION_DESCRIPTOR =
            TypeName.create("io.helidon.service.registry.Service.Descriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.GeneratedService.Descriptor}.
     */
    public static final TypeName SERVICE_DESCRIPTOR = TypeName.create("io.helidon.service.registry.GeneratedService.Descriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Dependency}.
     */
    public static final TypeName SERVICE_DEPENDENCY = TypeName.create("io.helidon.service.registry.Dependency");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.DependencyContext}.
     */
    public static final TypeName SERVICE_DEPENDENCY_CONTEXT = TypeName.create("io.helidon.service.registry.DependencyContext");

    private ServiceCodegenTypes() {
    }
}
