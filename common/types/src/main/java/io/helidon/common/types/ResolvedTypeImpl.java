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

import java.util.List;

class ResolvedTypeImpl implements ResolvedType {
    private final TypeName typeName;
    private final String resolvedName;
    private final boolean noTypes;

    ResolvedTypeImpl(TypeName typeName) {
        this.typeName = typeName;
        this.resolvedName = typeName.resolvedName();
        this.noTypes = typeName.typeArguments().isEmpty();
    }

    @Override
    public String resolvedName() {
        return resolvedName;
    }

    @Override
    public TypeName boxed() {
        return typeName.boxed();
    }

    @Override
    public TypeName genericTypeName() {
        return typeName.genericTypeName();
    }

    @Override
    public String packageName() {
        return typeName.packageName();
    }

    @Override
    public String className() {
        return typeName.className();
    }

    @Override
    public List<String> enclosingNames() {
        return typeName.enclosingNames();
    }

    @Override
    public boolean primitive() {
        return typeName.primitive();
    }

    @Override
    public boolean array() {
        return typeName.array();
    }

    @Override
    public boolean generic() {
        return typeName.generic();
    }

    @Override
    public boolean wildcard() {
        return typeName.wildcard();
    }

    @Override
    public List<TypeName> typeArguments() {
        return typeName.typeArguments();
    }

    @Override
    public List<String> typeParameters() {
        return typeName.typeParameters();
    }

    @Override
    public List<TypeName> lowerBounds() {
        return typeName.lowerBounds();
    }

    @Override
    public List<TypeName> upperBounds() {
        return typeName.upperBounds();
    }

    @Override
    public String declaredName() {
        return typeName.declaredName();
    }

    @Override
    public String classNameWithEnclosingNames() {
        return typeName.classNameWithEnclosingNames();
    }

    @Override
    public boolean isList() {
        return typeName.isList();
    }

    @Override
    public boolean isSet() {
        return typeName.isSet();
    }

    @Override
    public boolean isMap() {
        return typeName.isMap();
    }

    @Override
    public boolean isOptional() {
        return typeName.isOptional();
    }

    @Override
    public boolean isSupplier() {
        return typeName.isSupplier();
    }

    @Override
    public String classNameWithTypes() {
        return typeName.classNameWithTypes();
    }

    @Override
    public String name() {
        return typeName.name();
    }

    @Override
    public String fqName() {
        return typeName.fqName();
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
        if (!(o instanceof TypeName other)) {
            return false;
        }
        if (other instanceof ResolvedTypeImpl rti) {
            return resolvedName.equals(rti.resolvedName);
        } else {
            if (noTypes && other.typeArguments().isEmpty()) {
                return typeName.equals(other);
            }
            if (noTypes || other.typeArguments().isEmpty()) {
                return false;
            }
            return resolvedName.equals(other.resolvedName());
        }
    }

    @Override
    public int compareTo(TypeName o) {
        int diff = resolvedName.compareTo(o.resolvedName());
        if (diff != 0) {
            // different name
            return diff;
        }
        diff = Boolean.compare(typeName.primitive(), o.primitive());
        if (diff != 0) {
            return diff;
        }
        return Boolean.compare(typeName.array(), o.array());
    }

    @Override
    public String toString() {
        return resolvedName;
    }
}
