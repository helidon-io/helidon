/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen.scan;

import java.util.Objects;

import io.helidon.common.types.TypeName;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.HierarchicalTypeSignature;

/**
 * Factory for types based on classpath scanning.
 */
public final class ScanTypeFactory {
    private ScanTypeFactory() {
    }

    /**
     * Creates a name from a class info from classpath scanning.
     *
     * @param classInfo the element type
     * @return the associated type name instance
     */
    public static TypeName create(ClassInfo classInfo) {
        Objects.requireNonNull(classInfo);
        return TypeName.create(classInfo.getName().replace('$', '.'));
    }

    /**
     * Creates a type name for a classpath scanning type with possible generic declaration.
     *
     * @param signature signature to use
     * @return type name for the provided signature
     */
    public static TypeName create(HierarchicalTypeSignature signature) {
        return TypeName.create(signature.toString());
    }
}
