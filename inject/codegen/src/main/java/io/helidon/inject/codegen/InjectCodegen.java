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

package io.helidon.inject.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.codegen.spi.InjectCodegenExtension;
import io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider;

/**
 * Handles processing of all extensions, creates context and writes types.
 */
class InjectCodegen implements CodegenExtension {
    private final Map<TypeName, List<InjectCodegenExtension>> typeToExtensions = new HashMap<>();
    private final Map<InjectCodegenExtension, Predicate<TypeName>> extensionPredicates = new IdentityHashMap<>();
    private final Set<TypeName> generatedServiceDescriptors = new HashSet<>();
    private final TypeName generator;
    private final InjectionCodegenContext ctx;
    private final List<InjectCodegenExtension> extensions;
    private final String module;

    private InjectCodegen(CodegenContext ctx, TypeName generator, List<InjectCodegenExtensionProvider> extensions) {
        this.ctx = InjectionCodegenContext.create(ctx);
        this.generator = generator;
        this.module = ctx.moduleName().orElse(null);

        this.extensions = extensions.stream()
                .map(it -> {
                    InjectCodegenExtension extension = it.create(this.ctx);

                    for (TypeName typeName : it.supportedAnnotations()) {
                        typeToExtensions.computeIfAbsent(typeName, key -> new ArrayList<>())
                                .add(extension);
                    }
                    Collection<String> packages = it.supportedAnnotationPackages();
                    if (!packages.isEmpty()) {
                        extensionPredicates.put(extension, discoveryPredicate(packages));
                    }

                    return extension;
                })
                .toList();
    }

    static InjectCodegen create(CodegenContext ctx, TypeName generator, List<InjectCodegenExtensionProvider> extensions) {
        return new InjectCodegen(ctx, generator, extensions);
    }

    @Override
    public void process(io.helidon.codegen.RoundContext roundContext) {
        Collection<TypeInfo> allTypes = roundContext.types();
        if (allTypes.isEmpty()) {
            extensions.forEach(it -> it.process(createRoundContext(List.of(), it)));
            return;
        }

        // type info list will contain all mapped annotations, so this is the state we can do annotation processing on
        List<TypeInfoAndAnnotations> annotatedTypes = annotatedTypes(allTypes);

        // and now for each extension, we discover types that contain annotations supported by that extension
        // and create a new round context for each extension

        // for each extension, create a RoundContext with just the stuff it wants
        for (InjectCodegenExtension extension : extensions) {
            extension.process(createRoundContext(annotatedTypes, extension));
        }

        writeNewTypes();

        for (TypeInfo typeInfo : roundContext.annotatedTypes(InjectCodegenTypes.INJECTION_DESCRIPTOR)) {
            // add each declared descriptor in source code
            generatedServiceDescriptors.add(typeInfo.typeName());
        }

        if (roundContext.availableAnnotations().size() == 1 && roundContext.availableAnnotations()
                .contains(TypeNames.GENERATED)) {

            // no other types generated by Helidon annotation processors, we can generate module component (unless already done)
            if (!generatedServiceDescriptors.isEmpty()) {
                generateModuleComponent();
                generatedServiceDescriptors.clear();
            }
        }
    }

    @Override
    public void processingOver(io.helidon.codegen.RoundContext roundContext) {
        // do processing over in each extension
        extensions.forEach(InjectCodegenExtension::processingOver);

        // if there was any type generated, write it out (will not trigger next round)
        writeNewTypes();

        if (!generatedServiceDescriptors.isEmpty()) {
            // re-check, maybe we run from a tool that does not generate anything except for the module component,
            // so let's create it now anyway (if created above, the set of descriptors is empty, so it is not attempted again
            // if somebody adds a service descriptor when processingOver, than it is wrong anyway
            generateModuleComponent();
            generatedServiceDescriptors.clear();
        }
    }

    private static Predicate<TypeName> discoveryPredicate(Collection<String> packages) {
        List<String> prefixes = packages.stream()
                .map(it -> it.endsWith(".*") ? it.substring(0, it.length() - 2) : it)
                .toList();
        return typeName -> {
            String packageName = typeName.packageName();
            for (String prefix : prefixes) {
                if (packageName.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        };
    }

    private void generateModuleComponent() {
        // and write the module component
        Optional<ModuleInfo> currentModule = ctx.module();

        // generate module
        String moduleName = this.module == null ? currentModule.map(ModuleInfo::name).orElse(null) : module;
        String packageName = CodegenOptions.CODEGEN_PACKAGE.findValue(ctx.options())
                .orElseGet(() -> topLevelPackage(generatedServiceDescriptors));
        boolean hasModule = moduleName != null && !"unnamed module".equals(moduleName);
        if (!hasModule) {
            moduleName = "unnamed/" + packageName + (ctx.scope().isProduction() ? "" : "/" + ctx.scope().name());
        }
        ClassCode moduleComponent = ModuleComponentHandler.createClassModel(ctx.scope(),
                                                                            generatedServiceDescriptors,
                                                                            moduleName,
                                                                            packageName);
        // first generate the module component, then validate the module-info

        CodegenFiler filer = ctx.filer();

        ClassModel classModel = moduleComponent.classModel().build();
        filer.writeSourceFile(classModel, moduleComponent.originatingElements());

        if (!hasModule || CodegenOptions.CREATE_META_INF_SERVICES.value(ctx.options())) {
            // only create meta-inf/services if we are not a JPMS module

            try {
                filer.services(generator,
                               InjectCodegenTypes.MODULE_COMPONENT,
                               List.of(classModel.typeName()),
                               moduleComponent.originatingElements());
            } catch (Exception e) {
                // ignore this exception, as the resource probably exists and was done by the user
                ctx.logger()
                        .log(CodegenEvent.builder()
                                     .level(System.Logger.Level.DEBUG)
                                     .message("Failed to create services, probably already exists")
                                     .throwable(e)
                                     .build());
            }
        }

        if (hasModule && currentModule.isPresent()) {
            // check if we have `provider ModuleComponent with OurModuleComponent`
            ModuleInfo moduleInfo = currentModule.get();
            List<TypeName> typeNames = moduleInfo.provides()
                    .get(InjectCodegenTypes.MODULE_COMPONENT);
            boolean found = false;
            if (typeNames != null) {
                TypeName moduleComponentType = moduleComponent.newType();
                found = typeNames.stream()
                        .anyMatch(moduleComponentType::equals);
            }

            // now check if we provide an application from module info - if so, we need to generate a stub for it
            // as we create it too late (in a Maven plugin)
            List<TypeName> apps = moduleInfo.provides()
                    .get(InjectCodegenTypes.APPLICATION);
            if (apps != null && apps.size() == 1) {
                // only expect one, ignore other cases, probably some user specific approach
                TypeName appType = apps.getFirst();
                if (ctx.typeInfo(appType).isEmpty()) {
                    // application is declared in module-info.java, but not present on current classpath
                    generateApplicationStub(appType);
                }
            }

            if (!found) {
                throw new CodegenException("Please add \"provides " + InjectCodegenTypes.MODULE_COMPONENT.fqName()
                                                   + " with " + moduleComponent.newType().fqName() + ";\" "
                                                   + "to your module-info.java");
            }
        }
    }

    private void generateApplicationStub(TypeName appType) {
        ClassModel application = ClassModel.builder()
                .accessModifier(AccessModifier.PUBLIC)
                .type(appType)
                .addInterface(InjectCodegenTypes.APPLICATION)
                .addMethod(configure -> configure
                        .addAnnotation(Annotations.OVERRIDE)
                        .accessModifier(AccessModifier.PUBLIC)
                        .name("configure")
                        .addParameter(binder -> binder
                                .type(InjectCodegenTypes.SERVICE_INJECTION_PLAN_BINDER)
                                .name("binder")))
                .addMethod(name -> name
                        .addAnnotation(Annotations.OVERRIDE)
                        .accessModifier(AccessModifier.PUBLIC)
                        .returnType(TypeNames.STRING)
                        .name("name")
                        .addContentLine("return \"empty stub to be overwritten by Maven plugin\";"))
                .build();

        ctx.filer()
                .writeSourceFile(application);

    }

    private void writeNewTypes() {
        // after each round, write all generated types
        CodegenFiler filer = ctx.filer();

        // generate all code
        var builders = ctx.descriptors();
        for (var classCode : builders) {
            ClassModel classModel = classCode.classModel().build();
            generatedServiceDescriptors.add(classCode.newType());
            filer.writeSourceFile(classModel, classCode.originatingElements());
        }
        builders.clear();

        builders = ctx.types();
        for (var classCode : builders) {
            ClassModel classModel = classCode.classModel().build();
            filer.writeSourceFile(classModel, classCode.originatingElements());
        }
        builders.clear();
    }

    private List<TypeInfoAndAnnotations> annotatedTypes(Collection<TypeInfo> allTypes) {
        List<TypeInfoAndAnnotations> result = new ArrayList<>();

        for (TypeInfo typeInfo : allTypes) {
            result.add(new TypeInfoAndAnnotations(typeInfo, annotations(typeInfo)));
        }
        return result;
    }

    private RoundContext createRoundContext(List<TypeInfoAndAnnotations> annotatedTypes, InjectCodegenExtension extension) {
        Set<TypeName> extAnnots = new HashSet<>();
        Map<TypeName, List<TypeInfo>> extAnnotToType = new HashMap<>();
        Map<TypeName, TypeInfo> extTypes = new HashMap<>();

        for (TypeInfoAndAnnotations annotatedType : annotatedTypes) {
            for (TypeName typeName : annotatedType.annotations()) {
                boolean added = false;
                List<InjectCodegenExtension> validExts = this.typeToExtensions.get(typeName);
                if (validExts != null) {
                    for (InjectCodegenExtension validExt : validExts) {
                        if (validExt == extension) {
                            extAnnots.add(typeName);
                            extAnnotToType.computeIfAbsent(typeName, key -> new ArrayList<>())
                                    .add(annotatedType.typeInfo());
                            extTypes.put(annotatedType.typeInfo().typeName(), annotatedType.typeInfo);
                            added = true;
                        }
                    }
                }
                if (!added) {
                    Predicate<TypeName> predicate = this.extensionPredicates.get(extension);
                    if (predicate != null && predicate.test(typeName)) {
                        extAnnots.add(typeName);
                        extAnnotToType.computeIfAbsent(typeName, key -> new ArrayList<>())
                                .add(annotatedType.typeInfo());
                        extTypes.put(annotatedType.typeInfo().typeName(), annotatedType.typeInfo);
                    }
                }
            }
        }

        return new RoundContextImpl(
                Set.copyOf(extAnnots),
                Map.copyOf(extAnnotToType),
                List.copyOf(extTypes.values()));
    }

    private Set<TypeName> annotations(TypeInfo theTypeInfo) {
        Set<TypeName> result = new HashSet<>();

        // on type
        theTypeInfo.annotations()
                .stream()
                .map(Annotation::typeName)
                .forEach(result::add);

        // on fields, methods etc.
        theTypeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::annotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        // on parameters
        theTypeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::parameterArguments)
                .flatMap(List::stream)
                .map(TypedElementInfo::annotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        return result;
    }

    private String topLevelPackage(Set<TypeName> typeNames) {
        String thePackage = typeNames.iterator().next().packageName();

        for (TypeName typeName : typeNames) {
            String nextPackage = typeName.packageName();
            if (nextPackage.length() < thePackage.length()) {
                thePackage = nextPackage;
            }
        }

        return thePackage;
    }

    private record TypeInfoAndAnnotations(TypeInfo typeInfo, Set<TypeName> annotations) {
    }
}
