/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Core dependency can only be a constructor parameter.
 */
class CoreDependency {
    private final TypedElementInfo constructor;
    // type of the dependency (such as Contract; Optional<Contract>; List<Contract>)
    private final TypeName typeName;
    private final TypeName contract;
    // name of the dependency (parameter name)
    private final String name;
    // name of the constant in service descriptor (PARAM_0 = Dependency.builder()...)
    private final String dependencyConstant;
    // name of the constant in service descriptor declaring the contract to "inject" (TYPE_0 = TypeName.create...)
    private final String contractTypeConstant;
    // name of the constant in service descriptor declaring the generic type of contract to "inject"
    // (GTYPE_0 = new GenericType<...>)
    private final String genericTypeConstant;
    // full type name (including optional, supplier etc.)
    private final String typeNameConstant;

    CoreDependency(TypedElementInfo constructor,
                   TypeName typeName,
                   TypeName contract,
                   String name,
                   String dependencyConstant,
                   String contractTypeConstant,
                   String genericTypeConstant,
                   String typeNameConstant) {
        this.constructor = constructor;
        this.typeName = typeName;
        this.contract = contract;
        this.name = name;
        this.dependencyConstant = dependencyConstant;
        this.contractTypeConstant = contractTypeConstant;
        this.genericTypeConstant = genericTypeConstant;
        this.typeNameConstant = typeNameConstant;
    }

    static CoreDependency create(RegistryCodegenContext ctx,
                                 TypedElementInfo constructor,
                                 TypedElementInfo parameter,
                                 CoreTypeConstants constants,
                                 int index) {

        String dependencyConstant = "PARAM_" + index;
        TypeName paramTypeName = parameter.typeName();
        TypeName contract = contract(parameter, paramTypeName, false, false, false);

        String contractConstant = constants.typeNameConstant(ResolvedType.create(contract));
        String dependencyGenericTypeConstant = constants.genericTypeConstant(ResolvedType.create(paramTypeName));
        String typeNameConstant = constants.typeNameConstant(ResolvedType.create(paramTypeName));
        String paramName = parameter.elementName();

        return new CoreDependency(
                constructor,
                paramTypeName,
                contract,
                paramName,
                dependencyConstant,
                contractConstant,
                dependencyGenericTypeConstant,
                typeNameConstant);
    }

    public String typeNameConstant() {
        return typeNameConstant;
    }

    TypeName typeName() {
        return typeName;
    }

    TypeName contract() {
        return contract;
    }

    String name() {
        return name;
    }

    String dependencyConstant() {
        return dependencyConstant;
    }

    String contractTypeConstant() {
        return contractTypeConstant;
    }

    String genericTypeConstant() {
        return genericTypeConstant;
    }

    TypedElementInfo constructor() {
        return constructor;
    }

    /*
    get the contract expected for this dependency
    Dependency may be:
     - Optional<Contract>
     - List<Contract>
     - Supplier<Contract>
     - Supplier<Optional<Contract>>
     - Supplier<List<Contract>>
    */
    private static TypeName contract(TypedElementInfo parameter,
                                     TypeName typeName,
                                     boolean isList,
                                     boolean isOptional,
                                     boolean isSupplier) {
        String allowed = "Dependency can be only declared as either of: "
                + "Contract, Optional<Contract>, List<Contract>, Supplier<Contract>, "
                + "Supplier<Optional<Contract>, Supplier<List<Contract>>";

        if (typeName.isOptional()) {
            if (isList || isOptional) {
                throw new CodegenException(allowed,
                                           parameter.originatingElementValue());
            }
            if (typeName.typeArguments().isEmpty()) {
                throw new CodegenException("Dependency with Optional type must have a declared type argument.",
                                           parameter.originatingElementValue());
            }
            return contract(parameter, typeName.typeArguments().getFirst(), false, true, isSupplier);
        }
        if (typeName.isList()) {
            if (isList || isOptional) {
                throw new CodegenException(allowed,
                                           parameter.originatingElementValue());
            }
            if (typeName.typeArguments().isEmpty()) {
                throw new CodegenException("Dependency with List type must have a declared type argument.",
                                           parameter.originatingElementValue());
            }
            return contract(parameter, typeName.typeArguments().getFirst(), true, false, isSupplier);
        }
        if (typeName.isSupplier()) {
            if (isSupplier || isOptional || isList) {
                throw new CodegenException(allowed,
                                           parameter.originatingElementValue());
            }
            if (typeName.typeArguments().isEmpty()) {
                throw new CodegenException("Dependency with Supplier type must have a declared type argument.",
                                           parameter.originatingElementValue());
            }
            return contract(parameter, typeName.typeArguments().getFirst(), isList, isOptional, true);
        }

        return typeName;
    }
}
