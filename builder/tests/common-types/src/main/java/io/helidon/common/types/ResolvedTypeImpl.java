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

class ResolvedTypeImpl implements ResolvedType, Comparable<ResolvedType> {
    private final TypeName typeName;
    private final String resolvedName;
    private final boolean noTypes;

    ResolvedTypeImpl(TypeName typeName) {
        this.typeName = typeName;
        this.resolvedName = typeName.resolvedName();
        this.noTypes = typeName.typeArguments().isEmpty();
    }

    @Override
    public TypeName type() {
        return typeName;
    }

    @Override
    public String resolvedName() {
        return resolvedName;
    }

    @Override
    public int hashCode() {
        return noTypes ? typeName.hashCode() : resolvedName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ResolvedType other)) {
            return false;
        }
        if (other instanceof ResolvedTypeImpl rti) {
            return resolvedName.equals(rti.resolvedName);
        }
        return other.type().resolvedName().equals(resolvedName);
    }

    @Override
    public int compareTo(ResolvedType o) {
        int diff = resolvedName.compareTo(o.type().resolvedName());
        if (diff != 0) {
            // different name
            return diff;
        }
        diff = Boolean.compare(typeName.primitive(), o.type().primitive());
        if (diff != 0) {
            return diff;
        }
        return Boolean.compare(typeName.array(), o.type().array());
    }

    @Override
    public String toString() {
        return resolvedName;
    }
}
