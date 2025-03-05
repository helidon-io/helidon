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

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.LazyValue;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceGenerator.GENERATOR;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.DATA_EXCEPTION;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.DATA_SOURCE_ANNOTATION;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.INJECTION_SINGLETON;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.REPOSITORY_FACTORY;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.REPOSITORY_PROVIDER;

final class JakartaRepositoryProviderGenerator {
    private JakartaRepositoryProviderGenerator() {
    }

    static void generate(CodegenContext codegenContext,
                         RoundContext roundContext,
                         RepositoryGenerator repositoryGenerator,
                         RepositoryInfo repositoryInfo,
                         TypeName className,
                         TypeName repositoryClassName,
                         ClassModel.Builder classModel) {

        TypeName.Builder genericInterfaceBuilder = TypeName.builder()
                .from(repositoryGenerator.genericInterface(repositoryInfo.interfacesInfo()).typeName());
        genericInterfaceBuilder.typeArguments().clear();

        TypeName repoType = repositoryInfo.interfaceInfo().typeName();
        TypeName repositoryProviderType = TypeName.builder(REPOSITORY_PROVIDER)
                .addTypeArgument(repoType)
                .build();

        TypeName supplier = TypeName.builder()
                .from(TypeNames.SUPPLIER)
                .addTypeArgument(repositoryInfo.interfaceInfo().typeName())
                .build();
        classModel.type(className)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(INJECTION_SINGLETON)
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               repoType,
                                                               className,
                                                               "1",
                                                               ""))
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 repositoryInfo.interfaceInfo().typeName(),
                                                 className))
                .classType(ElementKind.CLASS)
                .accessModifier(AccessModifier.PUBLIC)
                .addInterface(repositoryProviderType)
                .addInterface(supplier)
                .addMethod(builder -> generateType(builder, repositoryInfo.interfaceInfo()))
                .addMethod(builder -> generateEntityClass(builder, repositoryInfo.entity()));

        boolean hasNamed = false;
        String name = null;
        boolean nameRequired = false;

        if (repositoryInfo.interfaceInfo().hasAnnotation(DATA_SOURCE_ANNOTATION)) {
            Annotation dataSourceAnnotation = repositoryInfo.interfaceInfo().annotation(DATA_SOURCE_ANNOTATION);

            hasNamed = true;
            name = dataSourceAnnotation.value().orElse("@default");
            nameRequired = dataSourceAnnotation.booleanValue("required").orElse(false);
        }

        generateConstructorAndFields(classModel, repoType, repositoryClassName, hasNamed, name, nameRequired);
        generateGet(classModel, repoType);
        if (hasNamed) {
            generateDataSourceName(classModel, name);
        }
        generateDataSupportType(classModel);
        generatePreDestroy(classModel);
    }

    private static void generateDataSupportType(ClassModel.Builder classModel) {
        classModel.addMethod(dsType -> dsType
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.STRING)
                .name("dataSupportType")
                .addContentLine("return \"jakarta\";")
        );
    }

    private static void generateDataSourceName(ClassModel.Builder classModel, String name) {
        classModel.addMethod(dsName -> dsName
                .name("dataSourceName")
                .returnType(TypeNames.STRING)
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .addContent("return \"")
                .addContent(name)
                .addContentLine("\";")
        );
    }

    private static void generatePreDestroy(ClassModel.Builder classModel) {
        classModel.addMethod(preDestroy -> preDestroy
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(JakartaPersistenceTypes.INJECTION_PRE_DESTROY)
                .name("preDestroy")
                .addContentLine("if (repository.isLoaded()) {")
                .addContentLine("repository.get().close();")
                .addContentLine("}")
        );
    }

    private static void generateConstructorAndFields(ClassModel.Builder builder,
                                                     TypeName repoType,
                                                     TypeName repositoryClassName,
                                                     boolean hasNamed,
                                                     String name,
                                                     boolean nameRequired) {

        // there is always the same field
        builder.addField(repository -> repository
                .name("repository")
                .type(lazyValue(repositoryClassName))
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
        );

        /*
        We may have one or two parameters depending on combinations, as follows:
        a) hasNamed && nameRequired - @Injection.Named("name") Supplier<JpaRepositoryExecutor> executor
        b) hasNamed && !nameRequired - @Injection.Named("name") Supplier<Optional<JpaRepositoryExecutor>> executor,
                                           Supplier<Optional<JpaRepositoryExecutor>> defaultExecutor
        c) !hasNamed - Supplier<JpaRepositoryExecutor> executor
         */
        TypeName parameterType;
        if (hasNamed && !nameRequired) {
            parameterType = TypeName.builder(TypeNames.SUPPLIER)
                    .addTypeArgument(TypeName.builder(TypeNames.OPTIONAL)
                                             .addTypeArgument(REPOSITORY_FACTORY)
                                             .build())
                    .build();
        } else {
            parameterType = TypeName.builder(TypeNames.SUPPLIER)
                    .addTypeArgument(REPOSITORY_FACTORY)
                    .build();
        }

        Constructor.Builder ctr = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);
        Annotation supportTypeQualifier = Annotation.create(TypeName.create("io.helidon.data.api.Data.SupportType"), "jakarta");
        if (hasNamed) {
            Annotation named = Annotation.builder()
                    .typeName(TypeName.create("io.helidon.service.inject.api.Injection.Named"))
                    .putValue("value", name)
                    .build();
            // first parameter always has the named annotation
            ctr.addParameter(factory -> factory
                    .name("factory")
                    .addAnnotation(supportTypeQualifier)
                    .addAnnotation(named)
                    .type(parameterType));

            if (!nameRequired) {
                ctr.addParameter(factory -> factory
                        .name("defaultFactory")
                        .type(parameterType));
            }
        } else {
            ctr.addParameter(factory -> factory
                    .name("factory")
                    .addAnnotation(supportTypeQualifier)
                    .type(parameterType));
        }

        if (!hasNamed || nameRequired) {
            ctr.addContent("this.repository = ")
                    .addContent(LazyValue.class)
                    .addContent(".create(() -> factory.get().create(")
                    .addContent(repositoryClassName)
                    .addContentLine("::new));");
        } else {
            ctr.addContent("this.repository = ")
                    .addContent(LazyValue.class)
                    .addContentLine(".create(() -> {")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine("var usedFactory = factory.get().or(defaultFactory::get);")
                    .addContentLine("")
                    .addContentLine("if (usedFactory.isEmpty()) {")
                    .addContent("throw new ")
                    .addContent(DATA_EXCEPTION)
                    .addContent("(\"Repository " + repoType.fqName() + " expects either a named data "
                                        + "configuration '")
                    .addContent(name)
                    .addContentLine("', or a default configuration, yet neither is available. Configure under 'data'"
                                            + " root configuration key.\");")
                    .addContentLine("}")
                    .addContentLine("")
                    .addContent("usedFactory.get().create(")
                    .addContent(repositoryClassName)
                    .addContentLine("::new);")
                    .decreaseContentPadding()
                    .decreaseContentPadding()
                    .addContentLine("});");
        }

        builder.addConstructor(ctr);
    }

    private static TypeName lazyValue(TypeName repositoryClassName) {
        return TypeName.builder(TypeName.create(LazyValue.class))
                .addTypeArgument(repositoryClassName)
                .build();
    }

    private static void generateType(Method.Builder builder, TypeInfo repositoryInfo) {
        builder.name("type")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(
                        TypeName.builder()
                                .type(Class.class)
                                .addTypeArgument(repositoryInfo.typeName())
                                .build())
                .addContent("return ")
                .addContent(repositoryInfo.typeName())
                .addContentLine(".class;");
    }

    private static void generateEntityClass(Method.Builder builder, TypeName entity) {
        builder.name("entityClass")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeName.builder()
                                    .type(Class.class)
                                    .addTypeArgument(TypeNames.WILDCARD)
                                    .build())
                .addContent("return ")
                .addContent(entity)
                .addContentLine(".class;");
    }

    private static void generateGet(ClassModel.Builder builder,
                                    TypeName returnType) {
        builder.addMethod(get -> get
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(returnType)
                .name("get")
                .addContentLine("return repository.get();"));
    }
}
