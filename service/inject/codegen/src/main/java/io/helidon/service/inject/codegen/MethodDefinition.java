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

package io.helidon.service.inject.codegen;

import java.util.List;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

import static io.helidon.codegen.CodegenUtil.capitalize;

/**
 * A relevant method definition.
 *
 * @param declaringType    type that declares this method
 * @param access           access modifier of the method
 * @param methodId         unique ID of a method within a type
 * @param constantName     name of the constant of this method
 * @param methodName       name of the method
 * @param overrides        whether it overrides another method
 * @param params           list of parameter definitions
 * @param isInjectionPoint whether this is an injection point
 * @param isFinal          whether the method is declared as final
 */
record MethodDefinition(TypeName declaringType,
                        AccessModifier access,
                        String methodId,
                        String constantName,
                        String methodName,
                        boolean overrides,
                        List<ParamDefinition> params,
                        boolean isInjectionPoint,
                        boolean isFinal) {
    public String invokeName() {
        return "invoke" + capitalize(methodId());
    }
}
