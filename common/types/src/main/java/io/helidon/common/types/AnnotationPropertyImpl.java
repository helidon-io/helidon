/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.Optional;

class AnnotationPropertyImpl implements AnnotationProperty {
    private final Object value;
    private final ConstantValue constantValue;

    AnnotationPropertyImpl(Object value) {
        this(value, null);
    }

    AnnotationPropertyImpl(Object value, ConstantValue constantValue) {
        this.value = value;
        this.constantValue = constantValue;
    }

    @Override
    public Object value() {
        return value;
    }

    @Override
    public Optional<ConstantValue> constantValue() {
        return Optional.ofNullable(constantValue);
    }

    @Override
    public String toString() {
        if (constantValue == null) {
            return String.valueOf(value);
        }
        return constantValue.type().fqName() + "." + constantValue.name();
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof AnnotationPropertyImpl api) {
            return Objects.equals(value, api.value);
        }
        return Objects.equals(obj, value);
    }
}
