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

package io.helidon.service.inject.codegen;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.inject.codegen.spi.InjectCodegenObserver;
import io.helidon.service.inject.codegen.spi.InjectCodegenObserverProvider;

import static io.helidon.service.inject.codegen.InjectCodegenTypes.EVENT_EMITTER;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.EVENT_MANAGER;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECTION_INJECT;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECTION_SINGLETON;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECT_QUALIFIER;

/**
 * {@link java.util.ServiceLoader} provider implementation that generates services for event emitters.
 */
public class EventEmitterObserverProvider implements InjectCodegenObserverProvider {
    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public EventEmitterObserverProvider() {
    }

    @Override
    public InjectCodegenObserver create(RegistryCodegenContext context) {
        return new EventEmitterObserver();
    }

    private static final class EventEmitterObserver implements InjectCodegenObserver {
        private static final TypeName GENERATOR = TypeName.create(EventEmitterObserver.class);
        private static final Map<ClassNameCacheKey, Map<Set<Annotation>, TypeName>> CACHE = new ConcurrentHashMap<>();

        private EventEmitterObserver() {
        }

        @Override
        public void onInjectionPoint(RegistryRoundContext roundContext,
                                     TypeInfo service,
                                     TypedElementInfo element,
                                     TypedElementInfo argument) {

            TypeName typeName = argument.typeName();
            if (typeName.equals(EVENT_EMITTER)) {
                if (typeName.typeArguments().isEmpty()) {
                    throw new CodegenException("Cannot generate an event emitter when type argument is missing",
                                               argument.originatingElementValue());
                }
                Set<Annotation> qualifiers = Qualifiers.qualifiers(argument);
                TypeName eventObject = typeName.typeArguments().getFirst();
                TypeName generatedType = emitterTypeName(service.typeName(), eventObject, qualifiers);
                if (roundContext.generatedType(generatedType).isEmpty()) {
                    // it may be already generated - maybe there are two injection points for the same event with same qualifiers
                    generateEventEmitter(roundContext, service, generatedType, service.typeName(), eventObject, qualifiers);
                }
            }
        }

        private static TypeName emitterTypeName(TypeName serviceType, TypeName eventObject, Set<Annotation> qualifiers) {
            ResolvedType event = ResolvedType.create(eventObject);

            var map = CACHE.computeIfAbsent(new ClassNameCacheKey(serviceType, event), k -> new ConcurrentHashMap<>());
            return map.computeIfAbsent(qualifiers, it -> {
                String className = serviceType.classNameWithEnclosingNames().replace('.', '_')
                        + "__Emitter";
                var builder = TypeName.builder()
                        .packageName(serviceType.packageName());
                if (map.isEmpty()) {
                    return builder.className(className)
                            .build();
                }
                return builder.className(className + "_" + map.size())
                        .build();
            });
        }

        private void generateEventEmitter(RegistryRoundContext roundContext,
                                          TypeInfo serviceInfo,
                                          TypeName generatedType,
                                          TypeName serviceTypeName,
                                          TypeName eventObject,
                                          Set<Annotation> qualifiers) {
            ClassModel.Builder classModel = ClassModel.builder()
                    .copyright(CodegenUtil.copyright(GENERATOR,
                                                     serviceTypeName,
                                                     generatedType))
                    .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                                   serviceTypeName,
                                                                   generatedType,
                                                                   "1",
                                                                   ""))
                    .type(generatedType)
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .description("Event emitter service for {@link " + eventObject.fqName() + "}.")
                    .addInterface(emitterInterface(eventObject))
                    .addAnnotation(Annotation.create(INJECTION_SINGLETON));

            // constant for event type
            classModel.addField(eventObjectConstant -> eventObjectConstant
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(TypeNames.RESOLVED_TYPE_NAME)
                    .name("EVENT_OBJECT")
                    .addContentCreate(ResolvedType.create(eventObject)));

            // qualifiers (if any)
            if (!qualifiers.isEmpty()) {
                Qualifiers.generateQualifiersConstant(classModel, qualifiers);
            }

            // event manager (must be injected)
            classModel.addField(eventManager -> eventManager
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(EVENT_MANAGER)
                    .name("manager"));

            // constructor
            classModel.addConstructor(ctr -> ctr
                    .addAnnotation(Annotation.create(INJECTION_INJECT))
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addParameter(eventManager -> eventManager
                            .type(EVENT_MANAGER)
                            .name("manager"))
                    .addContentLine("this.manager = manager;"));

            // emit methods
            boolean qualified = !qualifiers.isEmpty();
            addEmit(classModel, "emit", eventObject, TypeNames.PRIMITIVE_VOID, qualified);
            addEmit(classModel, "emitAsync", eventObject, completionStage(eventObject), qualified);
            if (qualified) {
                addMergeQualifiers(classModel);
                qualifiers.forEach(it -> classModel.addAnnotation(it));
            }

            roundContext.addGeneratedType(generatedType, classModel, serviceTypeName, serviceInfo);
        }

        private void addMergeQualifiers(ClassModel.Builder classModel) {
            classModel.addMethod(merge -> merge
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .name("mergeQualifiers")
                    .addParameter(qualifiers -> qualifiers
                            .name("qualifiers")
                            .type(INJECT_QUALIFIER)
                            .vararg(true))
                    .returnType(InjectionExtension.SET_OF_QUALIFIERS)
                    .addContentLine("if (qualifiers.length == 0) {")
                    .addContentLine("return QUALIFIERS;")
                    .addContentLine("}")
                    .addContent("var qualifierSet = new ")
                    .addContent(HashSet.class)
                    .addContentLine("<>(QUALIFIERS);")
                    .addContent("qualifierSet.addAll(")
                    .addContent(Set.class)
                    .addContentLine(".of(qualifiers));")
                    .addContentLine("return qualifierSet;")
            );
        }

        private void addEmit(ClassModel.Builder classModel,
                             String methodName,
                             TypeName eventObject,
                             TypeName returnType,
                             boolean isQualified) {
            classModel.addMethod(method -> method
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(returnType)
                    .name(methodName)
                    .addParameter(event -> event
                            .type(eventObject)
                            .name("eventObject"))
                    .addParameter(qualifiers -> qualifiers
                            .vararg(true)
                            .type(INJECT_QUALIFIER)
                            .name("qualifiers"))
                    .update(it -> {
                        if (!returnType.equals(TypeNames.PRIMITIVE_VOID)) {
                            it.addContent("return ");
                        }
                    })
                    .addContent("manager.")
                    .addContent(methodName)
                    .update(it -> {
                        if (isQualified) {
                            it.addContentLine("(EVENT_OBJECT, eventObject, mergeQualifiers(qualifiers));");
                        } else {
                            it.addContent("(EVENT_OBJECT, eventObject, ")
                                    .addContent(Set.class)
                                    .addContentLine(".of(qualifiers));");
                        }
                    }));
        }

        private TypeName completionStage(TypeName eventObject) {
            return TypeName.builder()
                    .from(TypeName.create(CompletionStage.class))
                    .addTypeArgument(eventObject)
                    .build();
        }

        private TypeName emitterInterface(TypeName eventObject) {
            return TypeName.builder()
                    .from(EVENT_EMITTER)
                    .addTypeArgument(eventObject)
                    .build();
        }
    }

    private record ClassNameCacheKey(TypeName serviceType, ResolvedType eventType) {
    }
}
