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

package io.helidon.service.codegen.javax;

import java.util.Set;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation for
 * {@link io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider} adding support for Jakarta types in
 * {@code javax} packages.
 * <p>
 * This provider adds a new service that creates a {@link java.util.function.Supplier} singleton for any provider discovered.
 */
public class JavaxExtensionProvider implements RegistryCodegenExtensionProvider {
    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public JavaxExtensionProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(ServiceCodegenTypes.INJECTION_SINGLETON,
                      ServiceCodegenTypes.INJECTION_INJECT,
                      ServiceCodegenTypes.INJECTION_DEPENDENT);
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext codegenContext) {
        return new JakartaExtension(codegenContext);
    }

    private static class JakartaExtension implements RegistryCodegenExtension {
        private final RegistryCodegenContext ctx;

        JakartaExtension(RegistryCodegenContext codegenContext) {
            this.ctx = codegenContext;
        }

        @Override
        public void process(RegistryRoundContext roundContext) {
            // we want to generate a new service for each provider that is a supplier
            for (TypeInfo type : roundContext.types()) {
                for (TypeInfo typeInfo : type.interfaceTypeInfo()) {
                    if (typeInfo.typeName().equals(JavaxTypes.INJECT_PROVIDER)) {
                        process(type);
                        break;
                    }
                }
            }
        }

        private void process(TypeInfo typeInfo) {
            TypeName sourceType = typeInfo.typeName();
            TypeName type = TypeName.builder()
                    .packageName(sourceType.packageName())
                    .className(sourceType.className() + "__InjectSupplier")
                    .build();
            TypeName providedType = providedType(typeInfo);

            ClassModel.Builder classModel = ClassModel.builder()
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .type(type)
                    .addInterface(supplier(providedType))
                    .addAnnotation(Annotation.create(ServiceCodegenTypes.INJECTION_SINGLETON));

            for (Annotation annotation : typeInfo.annotations()) {
                if (annotation.typeName().equals(ServiceCodegenTypes.INJECTION_SINGLETON)) {
                    // we do not want it twice
                    continue;
                }
                classModel.addAnnotation(annotation);
            }

            classModel.addField(provider -> provider
                    .name("provider")
                    .type(sourceType)
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
            );

            classModel.addConstructor(ctr -> ctr
                    .addAnnotation(Annotation.create(ServiceCodegenTypes.INJECTION_INJECT))
                    .addParameter(provider -> provider.name("provider")
                            .type(sourceType))
                    .addContentLine("this.provider = provider;")
            );

            classModel.addMethod(get -> get
                    .name("get")
                    .addAnnotation(Annotations.OVERRIDE)
                    .returnType(providedType)
                    .addContentLine("return provider.get();")
            );

            ctx.addType(type, classModel, sourceType, typeInfo.originatingElement().orElse(sourceType));
        }

        private TypeName providedType(TypeInfo type) {
            for (TypeInfo typeInfo : type.interfaceTypeInfo()) {
                if (typeInfo.typeName().equals(JavaxTypes.INJECT_PROVIDER)) {
                    // we assume this is a Provider<Something>, just Provider will fail, and does not make sense
                    return typeInfo.typeName()
                            .typeArguments()
                            .getFirst();
                }
            }
            throw new IllegalStateException("We should not get here, as we test first that we implement a Provider, and here"
                                                    + " we just get Provider type argument");
        }

        private TypeName supplier(TypeName type) {
            return TypeName.builder(TypeNames.SUPPLIER)
                    .addTypeArgument(type)
                    .build();
        }
    }
}
