/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.Collection;
import java.util.Optional;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Context of a single round of code generation.
 * For example the first round may generate types, that require additional code generation.
 */
public interface RoundContext {
    /**
     * Annotations available in this round, the collection contains only annotations valid for the extension being invoked.
     *
     * @return available annotations
     */
    Collection<TypeName> availableAnnotations();

    /**
     * All types that are processed in this round. Only contains types that are valid for processing by this extension.
     *
     * @return matching types
     */
    Collection<TypeInfo> types();

    /**
     * All types annotated with a specific annotation.
     *
     * @param annotationType annotation to check
     * @return types that contain the annotation
     */
    Collection<TypeInfo> annotatedTypes(TypeName annotationType);

    /**
     * All elements annotated with a specific annotation.
     *
     * @param annotationType annotation to check
     * @return elements that contain the annotation
     */
    Collection<TypedElementInfo> annotatedElements(TypeName annotationType);

    /**
     * Add a new class to be code generated.
     * <p>
     * Actual code generation will be done once, at the end of this round.
     * Note that you can always force immediate generation through {@link io.helidon.codegen.CodegenContext#filer()}. In such
     * a case do not add the type through this method.
     * If you call this method with a type that was already registered, you will replace that instance.
     *
     * @param type                type of the new class
     * @param newClass            builder of the new class
     * @param mainTrigger         a type that caused this, may be the processor itself, if not bound to any type
     * @param originatingElements possible originating elements  (such as Element in APT, or ClassInfo in classpath scanning)
     */
    void addGeneratedType(TypeName type, ClassModel.Builder newClass, TypeName mainTrigger, Object... originatingElements);

    /**
     * Class model builder for a type that is to be code generated.
     * This method provides access to all types that are to be generated, even from other extensions that do not match
     * annotations.
     * Whether another extension was already called depends on its {@link io.helidon.codegen.spi.CodegenExtensionProvider}
     * weight.
     *
     * @param type type of the generated type
     * @return class model of the new type if any
     */
    Optional<ClassModel.Builder> generatedType(TypeName type);
}
