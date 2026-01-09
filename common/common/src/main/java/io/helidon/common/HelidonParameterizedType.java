/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

class HelidonParameterizedType implements ParameterizedType {

    private final Class<?> type;
    private final Type[] typeArgs;

    HelidonParameterizedType(Class<?> type, Type[] typeArgs) {
        this.type = type;
        this.typeArgs = Arrays.copyOf(typeArgs, typeArgs.length);
    }

    @Override
    public Type[] getActualTypeArguments() {
        return typeArgs;
    }

    @Override
    public Type getRawType() {
        return type;
    }

    @Override
    public Type getOwnerType() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ParameterizedType) {
            // Check that information is equivalent
            ParameterizedType that = (ParameterizedType) o;

            if (this == that) {
                return true;
            }

            Type thatRawType = that.getRawType();

            return Objects.equals(type, thatRawType)
                    && Arrays.equals(typeArgs, that.getActualTypeArguments());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(typeArgs) ^ Objects.hashCode(type);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getName());
        if (typeArgs.length > 0) {
            sb.append("<");
            for (Type typeArg : typeArgs) {
                sb.append(typeArg.getTypeName());
            }
            sb.append(">");
        }
        return sb.toString();
    }

}
