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

package io.helidon.codegen.apt;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.lang.model.element.ModuleElement;

import io.helidon.codegen.TypeInfoFactoryBase;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ModuleInfoExports;
import io.helidon.common.types.ModuleInfoOpens;
import io.helidon.common.types.ModuleTypeInfo;
import io.helidon.common.types.TypeName;

@SuppressWarnings("removal")
class AptModuleFactory extends TypeInfoFactoryBase {
    private AptModuleFactory() {
    }

    static ModuleTypeInfo create(AptContext ctx, ModuleElement element) {

        List<Annotation> annotations = element.getAnnotationMirrors()
                .stream()
                .map(it -> AptAnnotationFactory.createAnnotation(it, ctx.aptEnv().getElementUtils()))
                .flatMap(it -> mapAnnotation(ctx, it, ElementKind.MODULE).stream())
                .collect(Collectors.toUnmodifiableList());
        String javadoc = ctx.aptEnv().getElementUtils().getDocComment(element);
        javadoc = javadoc == null || javadoc.isBlank() ? "" : javadoc;

        ModuleTypeInfo.Builder moduleBuilder = ModuleTypeInfo.builder()
                .originatingElement(element)
                .name(element.getQualifiedName().toString())
                .isOpen(element.isOpen())
                .description(javadoc)
                .annotations(annotations);

        List<? extends ModuleElement.Directive> directives = element.getDirectives();
        for (ModuleElement.Directive directive : directives) {
            switch (directive.getKind()) {
            case REQUIRES -> {
                ModuleElement.RequiresDirective requiresDirective = (ModuleElement.RequiresDirective) directive;
                moduleBuilder.addRequire(it -> it.isStatic(requiresDirective.isStatic())
                        .isTransitive(requiresDirective.isTransitive())
                        .dependency(requiresDirective.getDependency().getQualifiedName().toString()));
            }
            case EXPORTS -> {
                ModuleElement.ExportsDirective exportsDirective = (ModuleElement.ExportsDirective) directive;
                var exportsBuilder = ModuleInfoExports.builder()
                        .packageName(exportsDirective.getPackage().getQualifiedName().toString());
                List<? extends ModuleElement> targetModules = exportsDirective.getTargetModules();
                if (targetModules != null) {
                    targetModules.forEach(targetModule ->
                                                  exportsBuilder.addTarget(targetModule.getQualifiedName().toString()));
                }
                moduleBuilder.addExport(exportsBuilder.build());
            }
            case OPENS -> {
                ModuleElement.OpensDirective exportsDirective = (ModuleElement.OpensDirective) directive;
                var opensBuilder = ModuleInfoOpens.builder()
                        .packageName(exportsDirective.getPackage().getQualifiedName().toString());
                List<? extends ModuleElement> targetModules = exportsDirective.getTargetModules();
                if (targetModules != null) {
                    targetModules.forEach(targetModule ->
                                                  opensBuilder.addTarget(targetModule.getQualifiedName().toString()));
                }
            }
            case USES -> {
                ModuleElement.UsesDirective usesDirective = (ModuleElement.UsesDirective) directive;
                AptTypeFactory.createTypeName(usesDirective.getService())
                        .ifPresent(typeName -> moduleBuilder.addUse(it -> it.service(typeName)));
            }
            case PROVIDES -> {
                ModuleElement.ProvidesDirective providesDirective = (ModuleElement.ProvidesDirective) directive;
                Optional<TypeName> service = AptTypeFactory.createTypeName(providesDirective.getService());
                List<TypeName> impls = providesDirective.getImplementations()
                        .stream()
                        .map(AptTypeFactory::createTypeName)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toUnmodifiableList());
                if (service.isPresent()) {
                    moduleBuilder.addProvide(it -> it.service(service.get())
                            .addImplementations(impls));
                }
            }
            default -> {
                // ignore unknown type
            }
            }
        }

        return moduleBuilder.build();
    }
}
