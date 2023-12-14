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

package io.helidon.inject.codegen.jakarta;

import java.util.Optional;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.inject.codegen.InjectionCodegenContext.Assignment;
import io.helidon.inject.codegen.spi.InjectAssignment;
import io.helidon.inject.codegen.spi.InjectAssignmentProvider;

import static io.helidon.inject.codegen.jakarta.JakartaTypes.INJECT_PROVIDER;

/**
 * {@link io.helidon.inject.codegen.spi.InjectAssignmentProvider} provider implementation for Jakarta based projects.
 * This assignment provider adds support for Jakarta Provider injection points.
 */
public class JakartaAssignmentProvider implements InjectAssignmentProvider {
    /**
     * This constructor is required by {@link java.util.ServiceLoader}.
     *
     * @deprecated only for service loader
     */
    @Deprecated
    public JakartaAssignmentProvider() {
    }

    @Override
    public InjectAssignment create(CodegenContext ctx) {
        return new JakartaProviderMapper();
    }

    private static class JakartaProviderMapper implements InjectAssignment {

        @Override
        public Optional<Assignment> assignment(TypeName typeName, String valueSource) {
            // we support Provider<X>, Optional<Provider<X>>, and List<Provider<X>>
            if (typeName.isOptional()) {
                if (!typeName.typeArguments().isEmpty() && typeName.typeArguments().getFirst().equals(INJECT_PROVIDER)) {
                    return Optional.of(optionalProvider(typeName, valueSource));
                }
                return Optional.empty();
            }
            if (typeName.isList()) {
                if (!typeName.typeArguments().isEmpty() && typeName.typeArguments().getFirst().equals(INJECT_PROVIDER)) {
                    return Optional.of(listProvider(typeName, valueSource));
                }
                return Optional.empty();
            }
            if (typeName.genericTypeName().equals(INJECT_PROVIDER)) {
                return Optional.of(provider(typeName, valueSource));
            }
            return Optional.empty();
        }

        private Assignment optionalProvider(TypeName typeName, String valueSource) {
            TypeName actualType = typeName.typeArguments().getFirst() // Provider
                    .typeArguments().getFirst();

            TypeName replacementType = TypeName.builder(TypeNames.OPTIONAL)
                    .addTypeArgument(supplier(actualType))
                    .build();

            // Optional<Provider<NonSingletonService>> optionalProvider = // this code is generated always based on real type
            // ((Optional<Supplier<NonSingletonService>>) ctx.param(IP_PARAM_2))
            //                .map(it -> (Provider<NonSingletonService>) it::get)
            // ctx.param(IP_PARAM_0) is the "valueSource" provided to this method
            return new Assignment(replacementType,
                                  content -> content.addContent("((")
                                          .addContent(replacementType)
                                          .addContent(") ")
                                          .addContent(valueSource)
                                          .addContentLine(")")
                                          .increaseContentPadding()
                                          .increaseContentPadding()
                                          .addContent(".map(it -> (")
                                          .addContent(provider(actualType))
                                          .addContent(") it::get)")
                                          .decreaseContentPadding()
                                          .decreaseContentPadding());
        }

        private Assignment provider(TypeName typeName, String valueSource) {
            TypeName actualType = typeName.typeArguments().getFirst();
            TypeName replacementType = supplier(actualType);

            // Provider<NonSingletonService> provider =  // this code is generated always based on real type
            // ((Supplier<NonSingletonService>) ctx.param(IP_PARAM_0))::get;
            // ctx.param(IP_PARAM_0) is the "valueSource" provided to this method
            return new Assignment(replacementType,
                                  content -> content.addContent("((")
                                          .addContent(replacementType)
                                          .addContent(") ")
                                          .addContent(valueSource)
                                          .addContent(")::get"));
        }

        private Assignment listProvider(TypeName typeName, String valueSource) {
            TypeName actualType = typeName.typeArguments().getFirst() // Provider
                    .typeArguments().getFirst();

            TypeName replacementType = TypeName.builder(TypeNames.LIST)
                    .addTypeArgument(supplier(actualType))
                    .build();

            // List<Provider<NonSingletonService>> listOfProviders = // this code is generated always based on real type
            // ((List<Supplier<NonSingletonService>>) ctx.param(IP_PARAM_1))
            //                .stream()
            //                .map(it -> (Provider<NonSingletonService>) it::get)
            //                .toList();
            // ctx.param(IP_PARAM_1) is the "valueSource" provided to this method
            return new Assignment(replacementType,
                                  content -> content.addContent("((")
                                          .addContent(replacementType)
                                          .addContent(") ")
                                          .addContent(valueSource)
                                          .addContentLine(")")
                                          .increaseContentPadding()
                                          .increaseContentPadding()
                                          .addContentLine(".stream()")
                                          .addContent(".map(it -> (")
                                          .addContent(provider(actualType))
                                          .addContentLine(") it::get)")
                                          .addContent(".toList()")
                                          .decreaseContentPadding()
                                          .decreaseContentPadding());
        }

        private TypeName supplier(TypeName of) {
            return TypeName.builder(TypeNames.SUPPLIER)
                    .addTypeArgument(of)
                    .build();
        }

        private TypeName provider(TypeName of) {
            return TypeName.builder(INJECT_PROVIDER)
                    .addTypeArgument(of)
                    .build();
        }
    }
}
