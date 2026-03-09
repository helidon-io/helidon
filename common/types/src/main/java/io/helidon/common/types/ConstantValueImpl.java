/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.types.AnnotationProperty.ConstantValue;

final class ConstantValueImpl implements ConstantValue {
    private final TypeName type;
    private final String name;
    private final Object value;

    ConstantValueImpl(TypeName type, String name, Object value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }

    @Override
    public TypeName type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConstantValueImpl that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return type.fqName() + "." + name;
    }
}
