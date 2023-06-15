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

package io.helidon.builder.processor;

import java.util.List;
import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

record GeneratedMethod(Set<String> modifiers,
                       String genericDeclaration,
                       String name,
                       TypeName returnType,
                       List<Argument> arguments,
                       List<Annotation> annotations,
                       Javadoc javadoc,
                       List<String> methodLines) {

    GeneratedMethod(Set<String> modifiers,
                    String name,
                    TypeName returnType,
                    List<Argument> arguments,
                    List<Annotation> annotations,
                    Javadoc javadoc,
                    List<String> methodLines) {
        this(modifiers, null, name, returnType, arguments, annotations, javadoc, methodLines);
    }

    record Argument(String name,
                    TypeName typeName,
                    List<String> annotations) {

        Argument(String name, TypeName typeName) {
            this(name, typeName, List.of());
        }
    }
}
