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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.spi.InjectCodegenObserver;
import io.helidon.service.codegen.spi.InjectCodegenObserverProvider;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPTION_EXTERNAL_DELEGATE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_DESCRIBE;

class ServiceExtension implements RegistryCodegenExtension {
    private final RegistryCodegenContext ctx;
    private final InterceptionSupport interceptionSupport;
    private final List<InjectCodegenObserver> observers;
    private final ServiceDescriptorCodegen descriptorCodegen;

    ServiceExtension(RegistryCodegenContext codegenContext, List<InjectCodegenObserverProvider> observerProviders) {
        this.ctx = codegenContext;
        this.observers = observerProviders.stream()
                .map(it -> it.create(codegenContext))
                .toList();

        CodegenOptions options = ctx.options();
        Interception interception = new Interception(ServiceOptions.INTERCEPTION_STRATEGY.value(options));

        this.interceptionSupport = new InterceptionSupport(ctx, interception);
        this.descriptorCodegen = new ServiceDescriptorCodegen(ctx,
                                                              observers,
                                                              interceptionSupport);
    }

    @Override
    public void process(RegistryRoundContext roundCtx) {

        Collection<TypeInfo> descriptorsRequired = roundCtx.types();

        for (TypeInfo typeInfo : descriptorsRequired) {
            if (typeInfo.hasAnnotation(INTERCEPTION_EXTERNAL_DELEGATE)) {
                generateInterceptionExternalDelegates(roundCtx, typeInfo);
            }
            if (typeInfo.hasAnnotation(SERVICE_ANNOTATION_DESCRIBE)) {
                descriptorCodegen.describe(roundCtx,
                                           typeInfo,
                                           typeInfo.annotation(SERVICE_ANNOTATION_DESCRIBE));
            } else {
                descriptorCodegen.service(roundCtx,
                                          descriptorsRequired,
                                          typeInfo);
            }
        }

        notifyObservers(roundCtx, descriptorsRequired);
    }

    private void generateInterceptionExternalDelegates(RegistryRoundContext roundContext, TypeInfo typeInfo) {
        Annotation annotation = typeInfo.annotation(INTERCEPTION_EXTERNAL_DELEGATE);
        List<TypeName> typeNames = annotation.typeValues().orElseGet(List::of);
        boolean supportClasses = annotation.booleanValue("classDelegates").orElse(false);

        for (TypeName typeName : typeNames) {
            TypeInfo delegateType = ctx.typeInfo(typeName)
                    .orElseThrow(() -> new CodegenException("Cannot resolve type " + typeName.fqName() + " for "
                                                                    + " external interception delegates",
                                                            typeInfo.originatingElementValue()));
            if (!supportClasses && typeInfo.kind() != ElementKind.INTERFACE) {
                throw new CodegenException("Attempting to create external delegate interception for non interface type: "
                                                   + typeName.fqName(),
                                           typeInfo.originatingElementValue());
            }
            interceptionSupport.generateDelegateInterception(roundContext,
                                                             delegateType,
                                                             delegateType.typeName(),
                                                             typeInfo.typeName().packageName());
        }
    }

    private void notifyObservers(RegistryRoundContext roundContext, Collection<TypeInfo> descriptorsRequired) {
        if (observers.isEmpty()) {
            return;
        }

        // we have correct classloader set in current thread context
        Set<TypedElementInfo> elements = descriptorsRequired.stream()
                .flatMap(it -> it.elementInfo().stream())
                .collect(Collectors.toSet());
        observers.forEach(it -> it.onProcessingEvent(roundContext, elements));
    }
}
