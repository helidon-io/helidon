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

package io.helidon.service.codegen;

import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenContextDelegate;
import io.helidon.common.types.TypeName;

class RegistryCodegenContextImpl extends CodegenContextDelegate implements RegistryCodegenContext {
    RegistryCodegenContextImpl(CodegenContext context) {
        super(context);
    }

    @Override
    public TypeName descriptorType(TypeName serviceType) {
        // type is generated in the same package with a name suffix

        return TypeName.builder()
                .packageName(serviceType.packageName())
                .className(descriptorClassName(serviceType))
                .build();
    }

    private static String descriptorClassName(TypeName typeName) {
        // for MyType.MyService -> MyType_MyService__ServiceDescriptor

        List<String> enclosing = typeName.enclosingNames();
        String namePrefix;
        if (enclosing.isEmpty()) {
            namePrefix = "";
        } else {
            namePrefix = String.join("_", enclosing) + "_";
        }
        return namePrefix
                + typeName.className()
                + "__ServiceDescriptor";
    }

}
