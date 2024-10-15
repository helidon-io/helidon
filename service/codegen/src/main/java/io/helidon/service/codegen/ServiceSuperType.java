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

import java.util.Objects;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Definition of a super service type (if any).
 * Only classes can have super types, and only if they directly extend a class that has a service descriptor generated.
 */
public final class ServiceSuperType {
    private final TypeInfo typeInfo;
    private final String descriptorType;
    private final TypeName descriptorTypeName;

    private ServiceSuperType(TypeInfo typeInfo, String descriptorType, TypeName descriptoryTypeName) {
        this.descriptorType = descriptorType;
        this.descriptorTypeName = descriptoryTypeName;
        this.typeInfo = typeInfo;
    }

    /**
     * Create a registry based super type.
     *
     * @param typeInfo           type info of the super type
     * @param descriptorType     descriptor type (core, inject etc.) of the descriptor
     * @param descriptorTypeName type name of the service descriptor of the extended type
     * @return a new super type for a real super type
     */
    public static ServiceSuperType create(TypeInfo typeInfo, String descriptorType, TypeName descriptorTypeName) {
        Objects.requireNonNull(typeInfo);
        Objects.requireNonNull(descriptorType);
        Objects.requireNonNull(descriptorTypeName);

        return new ServiceSuperType(typeInfo, descriptorType, descriptorTypeName);
    }

    /**
     * Create a super type that represents "no supertype" (i.e. the only supertype is {@link java.lang.Object}).
     *
     * @return super type that is not present
     */
    public static ServiceSuperType create() {
        return new ServiceSuperType(null, null, null);
    }

    /**
     * Whether there is a super service type.
     *
     * @return if this service has a valid service super type
     */
    public boolean present() {
        return typeInfo != null;
    }

    /**
     * Whether there is NOT a super service type.
     *
     * @return if the service does not have a valid super type
     */
    public boolean empty() {
        return typeInfo == null;
    }

    /**
     * Type of the service descriptor, either {@code core} for core service registry, or other depending on supported
     * types (such as {@code inject}).
     *
     * @return type of the service
     */
    public String serviceType() {
        return descriptorType == null ? "core" : descriptorType;
    }

    /**
     * Type information of the super service type of this service.
     *
     * @return type info of the super service type
     * @throws java.lang.IllegalStateException if this is not a valid super type, guard with {@link #present()}
     */
    public TypeInfo typeInfo() {
        if (typeInfo == null) {
            throw new IllegalStateException("TypeInfo is only available if a service has a valid super type, please guard"
                                                    + " with SuperServiceType#present().");
        }
        return typeInfo;
    }

    /**
     * Type name of the service descriptor of the super service type.
     *
     * @return type name of the service descriptor for the super service type
     * @throws java.lang.IllegalStateException if this is not a valid super type, guard with {@link #present()}
     */
    public TypeName descriptorType() {
        if (typeInfo == null) {
            throw new IllegalStateException("Descriptor TypeName is only available if a service has a valid super type,"
                                                    + " please guard with SuperServiceType#present().");
        }
        return descriptorTypeName;
    }
}
