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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

import static io.helidon.service.codegen.ServiceCodegenTypes.EVENT_MANAGER;
import static io.helidon.service.codegen.ServiceCodegenTypes.EVENT_OBSERVER;
import static io.helidon.service.codegen.ServiceCodegenTypes.EVENT_OBSERVER_ASYNC;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_G_EVENT_OBSERVER_REGISTRATION;

/**
 * {@link java.util.ServiceLoader} provider implementation that adds support for generating event observer registrations.
 */
public class EventObserverExtensionProvider implements RegistryCodegenExtensionProvider {
    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public EventObserverExtensionProvider() {
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext codegenContext) {
        return new EventObserverExtension();
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(EVENT_OBSERVER,
                      EVENT_OBSERVER_ASYNC);
    }

    private static final class EventObserverExtension implements RegistryCodegenExtension {
        private static final TypeName GENERATOR = TypeName.create(EventObserverExtensionProvider.EventObserverExtension.class);
        private static final Map<ClassNameCacheKey, Map<Set<Annotation>, TypeName>> CACHE = new ConcurrentHashMap<>();

        @Override
        public void process(RegistryRoundContext roundContext) {
            Collection<TypedElementInfo> elements = roundContext.annotatedElements(EVENT_OBSERVER);
            process(roundContext, elements, "");
            elements = roundContext.annotatedElements(EVENT_OBSERVER_ASYNC);
            process(roundContext, elements, "Async");
        }

        private static TypeName registration(TypeName serviceType, TypeName eventObject, Set<Annotation> qualifiers) {
            ResolvedType event = ResolvedType.create(eventObject);

            var map = CACHE.computeIfAbsent(new ClassNameCacheKey(serviceType, event), k -> new ConcurrentHashMap<>());
            return map.computeIfAbsent(qualifiers, it -> {
                String className = serviceType.classNameWithEnclosingNames().replace('.', '_')
                        + "__Observer";
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

        private void process(RegistryRoundContext roundContext, Collection<TypedElementInfo> elements, String suffix) {
            for (TypedElementInfo element : elements) {
                if (element.kind() != ElementKind.METHOD) {
                    throw new CodegenException("Event observer annotations are only allowed on methods",
                                               element.originatingElementValue());
                }
                if (element.accessModifier() == AccessModifier.PRIVATE) {
                    throw new CodegenException("Event observer annotations are only allowed on non-private methods",
                                               element.originatingElementValue());
                }
                if (!element.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                    throw new CodegenException("Event observer annotations are only allowed on void methods",
                                               element.originatingElementValue());
                }
                if (element.parameterArguments().size() != 1) {
                    throw new CodegenException("Event observer annotations are only allowed on methods with exactly one "
                                                       + "parameter",
                                               element.originatingElementValue());
                }
                TypedElementInfo parameter = element.parameterArguments().getFirst();
                TypeName eventObject = parameter.typeName();
                Set<Annotation> qualifiers = Qualifiers.qualifiers(element);
                TypeInfo owningType = element.enclosingType()
                        .flatMap(roundContext::typeInfo)
                        .orElseThrow(() -> new CodegenException("Could not obtain type defining an observer",
                                                                element.originatingElementValue()));
                generateObserverRegistration(roundContext, owningType, element, qualifiers, eventObject, suffix);
            }
        }

        private void generateObserverRegistration(RegistryRoundContext roundContext,
                                                  TypeInfo owningType,
                                                  TypedElementInfo element,
                                                  Set<Annotation> qualifiers,
                                                  TypeName eventObject,
                                                  String suffix) {
            TypeName serviceTypeName = owningType.typeName();
            TypeName generatedType = registration(serviceTypeName, eventObject, qualifiers);

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
                    .description("Event observer registration service for {@link " + eventObject.fqName() + "}.")
                    .addInterface(SERVICE_G_EVENT_OBSERVER_REGISTRATION)
                    .addAnnotation(Annotation.create(SERVICE_ANNOTATION_SINGLETON));

            // constant for event type
            classModel.addField(eventObjectConstant -> eventObjectConstant
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(TypeNames.RESOLVED_TYPE_NAME)
                    .name("EVENT_OBJECT")
                    .addContentCreate(ResolvedType.create(eventObject)));

            // qualifiers (if any)
            Qualifiers.generateQualifiersConstant(classModel, qualifiers);

            // service field
            classModel.addField(eventObserver -> eventObserver
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(serviceTypeName)
                    .name("eventObserver")
            );

            // constructor
            classModel.addConstructor(ctr -> ctr
                    .addAnnotation(Annotation.create(SERVICE_ANNOTATION_INJECT))
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addParameter(eventObserver -> eventObserver
                            .type(serviceTypeName)
                            .name("eventObserver"))
                    .addContentLine("this.eventObserver = eventObserver;"));

            // and the register method to register it
            classModel.addMethod(register -> register
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.PRIMITIVE_VOID)
                    .name("register")
                    .addParameter(eventManager -> eventManager
                            .type(EVENT_MANAGER)
                            .name("manager"))
                    .addContent("manager.register")
                    .addContent(suffix)
                    .addContent("(EVENT_OBJECT, eventObserver::")
                    .addContent(element.elementName())
                    .addContentLine(", QUALIFIERS);")
            );

            roundContext.addGeneratedType(generatedType, classModel, serviceTypeName, owningType);
        }

        private record ClassNameCacheKey(TypeName serviceType, ResolvedType eventType) {
        }
    }
}
