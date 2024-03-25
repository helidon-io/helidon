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

package io.helidon.builder.codegen;

import java.util.Optional;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;

import static io.helidon.common.types.TypeNames.LIST;

class TypeHandlerList extends TypeHandlerCollection {

    TypeHandlerList(String name, String getterName, String setterName, TypeName declaredType) {
        super(name, getterName, setterName, declaredType, LIST, "toList()", Optional.empty());
    }

    static String isMutatedField(String propertyName) {
        return "is" + CodegenUtil.capitalize(propertyName) + "Mutated";
    }

    @Override
    Method.Builder extraAdderContent(Method.Builder builder) {
        return builder.addContentLine(isMutatedField() + " = true;");
    }

    @Override
    Method.Builder extraSetterContent(Method.Builder builder) {
        return builder.addContentLine(isMutatedField() + " = true;");
    }

    private String isMutatedField() {
        return isMutatedField(name());
    }
}
