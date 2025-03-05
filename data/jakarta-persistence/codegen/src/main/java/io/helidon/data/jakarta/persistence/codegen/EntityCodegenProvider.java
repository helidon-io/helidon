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

package io.helidon.data.jakarta.persistence.codegen;

import java.util.Collection;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.INJECTION_SINGLETON;

/**
 * Codegen for entity providers.
 * Each entity (a class annotated with {@code @Entity}) will have a provider generated, that
 * can be discovered from the service registry.
 */
public class EntityCodegenProvider implements CodegenExtensionProvider {
    private static final TypeName ENTITY = TypeName.create("jakarta.persistence.Entity");

    @Override
    public CodegenExtension create(CodegenContext codegenContext, TypeName typeName) {
        return new EntityCodegen();
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(ENTITY);
    }

    private static final class EntityCodegen implements CodegenExtension {
        private static final TypeName GENERATOR = TypeName.create(EntityCodegen.class);
        private static final TypeName ENTITY_PROVIDER = TypeName.create(
                "io.helidon.data.jakarta.persistence.gapi.JpaEntityProvider");

        private EntityCodegen() {
        }

        @Override
        public void process(RoundContext roundContext) {
            Collection<TypeInfo> typeInfos = roundContext.annotatedTypes(ENTITY);
            for (TypeInfo typeInfo : typeInfos) {
                createProvider(roundContext, typeInfo);
            }
        }

        private void createProvider(RoundContext roundContext, TypeInfo typeInfo) {
            TypeName trigger = typeInfo.typeName();
            TypeName generatedType = generatedEntityProviderType(trigger);

            var classModel = ClassModel.builder()
                    .copyright(CodegenUtil.copyright(GENERATOR, trigger, generatedType))
                    .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, trigger, generatedType, "1", ""))
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .description("Entity provider for {@link " + trigger.fqName() + "}.")
                    .type(generatedType)
                    .addInterface(providerInterfaceType(trigger))
                    .addAnnotation(INJECTION_SINGLETON);

            classModel.addConstructor(ctr -> ctr
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addContentLine("// This line is to ensure GraalVM native image can add this for reflection "
                                            + "without additional configuration")
                    .addContentLine("// It is needed as JPA (in this version) only supports string definition of entities")
                    .addContentLine("try {")
                    .addContent(Class.class)
                    .addContent(".forName(\"")
                    .addContent(trigger.fqName())
                    .addContentLine("\");")
                    .addContent("} catch (")
                    .addContent(Exception.class)
                    .addContentLine(" ignored) {")
                    .addContentLine("// ignored here, will fail when initializing JPA")
                    .addContentLine("}")
            );

            classModel.addMethod(entityClass -> entityClass
                    .name("entityClass")
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(entityClassType(trigger))
                    .addContent("return ")
                    .addContent(trigger)
                    .addContentLine(".class;")
            );

            roundContext.addGeneratedType(generatedType, classModel, trigger, typeInfo.originatingElementValue());
        }

        private TypeName entityClassType(TypeName trigger) {
            return TypeName.builder(TypeName.create(Class.class))
                    .addTypeArgument(trigger)
                    .build();
        }

        private TypeName generatedEntityProviderType(TypeName trigger) {
            return TypeName.builder()
                    .packageName(trigger.packageName())
                    .className(trigger.classNameWithEnclosingNames() + "__EntityProvider")
                    .build();
        }

        private TypeName providerInterfaceType(TypeName trigger) {
            return TypeName.builder(ENTITY_PROVIDER)
                    .addTypeArgument(trigger)
                    .build();
        }
    }
}
