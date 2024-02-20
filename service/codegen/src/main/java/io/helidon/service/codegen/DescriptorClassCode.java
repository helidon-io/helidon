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

package io.helidon.service.codegen;

import java.util.Set;

import io.helidon.codegen.ClassCode;
import io.helidon.common.types.TypeName;

/**
 * New service descriptor metadata with its class code.
 */
public interface DescriptorClassCode {
    /**
     * New source code information.
     *
     * @return class code
     */
    ClassCode classCode();

    /**
     * Type of registry of this descriptor.
     *
     * @return registry type
     */
    String registryType();

    /**
     * Weight of the new descriptor.
     *
     * @return weight
     */
    double weight();

    /**
     * Contracts the described service implements/provides.
     *
     * @return contracts of the service
     */
    Set<TypeName> contracts();
}
