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

package io.helidon.service.codegen;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

/**
 * An element capable of handling constants on a type.
 */
public interface FieldHandler {
    /**
     * Create a new constant handler for a class.
     * The constant handler ignores existing constants, or constants created outside of it.
     *
     * @param classModel class model builder
     * @param constructor constructor builder (of the class we are processing), to add assignments and parameters if needed
     * @return a new constant handler
     */
    static FieldHandler create(ClassModel.Builder classModel, Constructor.Builder constructor) {
        return new FieldHandlerImpl(classModel, constructor);
    }

    /**
     * Creates (or re-uses) a private constant in the generated class.
     * <p>
     * In case a new constant is needed, and there is a name conflict, a number will be used to uniquely
     * identify the constant.
     *
     * @param constantNamePrefix prefix for the constant name, such as {@code HEADER_X_TEST}
     * @param constantType       type of the constant, such as type of {@code HeaderName}
     * @param uniqueIdentifier   unique identification of the constant within its type, must have correct equals and hashCode
     *                           methods
     * @param contentBuilder     content builder to initialize the constant if it does not yet exist
     * @return constant name
     */
    String constant(String constantNamePrefix,
                    TypeName constantType,
                    Object uniqueIdentifier,
                    Consumer<ContentBuilder<?>> contentBuilder);

    /**
     * Creates (or re-uses) a private final field in the generated class.
     * <p>
     * In case a new field is needed, and there is a name conflict, a number will be used to uniquely identify the field.
     *
     * @param typeName           type of the field
     * @param fieldName          name of the field
     * @param modifier           modifier of the declaration
     * @param uniqueIdentifier   unique identification of the field within its type, must have correct equals and hashCode
     *                           methods
     * @param fieldUpdater       consumer of the field content builder (if it is initialized inlined)
     * @param constructorUpdater bi-consumer of the constructor builder (if it is initialized in constructor),
     *                           and the field name
     * @return field name
     */
    String field(TypeName typeName,
                 String fieldName,
                 AccessModifier modifier,
                 Object uniqueIdentifier,
                 Consumer<ContentBuilder<?>> fieldUpdater,
                 BiConsumer<Constructor.Builder, String> constructorUpdater);
}
