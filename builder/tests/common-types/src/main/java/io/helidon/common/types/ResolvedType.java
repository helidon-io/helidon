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

package io.helidon.common.types;

import java.lang.reflect.Type;

/**
 * A wrapper for {@link io.helidon.common.types.TypeName} that uses the resolved name for equals and hashCode.
 * This allows us to collect interfaces including type arguments.
 *
 * @see TypeName#resolvedName()
 */
public interface ResolvedType {
    /**
     * Create a type name from a type (such as class).
     *
     * @param type the type
     * @return type name for the provided type
     */
    static ResolvedType create(Type type) {
        return new ResolvedTypeImpl(TypeName.create(type));
    }

    /**
     * Creates a type name from a fully qualified class name.
     *
     * @param typeName the FQN of the class type
     * @return the TypeName for the provided type name
     */
    static ResolvedType create(String typeName) {
        return new ResolvedTypeImpl(TypeName.create(typeName));
    }

    /**
     * Create a type name from a type name.
     *
     * @param typeName the type
     * @return type name for the provided type
     */
    static ResolvedType create(TypeName typeName) {
        if (typeName instanceof ResolvedType rt) {
            return rt;
        }
        return new ResolvedTypeImpl(typeName);
    }

    /**
     * Provides the underlying type name that backs this resolved type.
     *
     * @return the type name this resolved type represents
     */
    TypeName type();
}
