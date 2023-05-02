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

package io.helidon.pico.configdriven.processor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.configdriven.api.ConfiguredBy;
import io.helidon.pico.configdriven.runtime.AbstractConfiguredServiceProvider;
import io.helidon.pico.processor.PicoAnnotationProcessor;
import io.helidon.pico.tools.ActivatorCreatorProvider;
import io.helidon.pico.tools.ServicesToProcess;

import static io.helidon.common.types.AnnotationAndValueDefault.findFirst;
import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.common.types.TypeNameDefault.createFromGenericDeclaration;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static io.helidon.common.types.TypeNameDefault.toBuilder;
import static io.helidon.pico.configdriven.processor.ConfiguredByProcessorUtils.createExtraActivatorClassComments;
import static io.helidon.pico.configdriven.processor.ConfiguredByProcessorUtils.createExtraCodeGen;

/**
 * Extension to {@link PicoAnnotationProcessor} that will handled {@link io.helidon.pico.configdriven.api.ConfiguredBy} services.
 */
// NOTE: This will be renamed to simply ConfiguredByProcessor once the LegacyConfiguredByProcessor is removed
public class PicoConfiguredByAnnotationProcessor extends PicoAnnotationProcessor {

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public PicoConfiguredByAnnotationProcessor() {
        super(true);
    }

    @Override
    protected Set<String> supportedServiceClassTargetAnnotations() {
        Set<String> supported = new LinkedHashSet<>(super.supportedServiceClassTargetAnnotations());
        supported.add(ConfiguredBy.class.getName());
        return supported;
    }

    @Override
    protected void processExtensions(ServicesToProcess services,
                                     TypeInfo service,
                                     Set<TypeName> serviceTypeNamesToCodeGenerate,
                                     List<TypedElementName> allElementsOfInterest) {
        Optional<? extends AnnotationAndValue> configuredByAnno = findFirst(ConfiguredBy.class, service.annotations());
        if (configuredByAnno.isEmpty()) {
            return;
        }

        Map<String, String> configuredByAttributes = configuredByAnno.get().values();
        TypeName configBeanType = createFromTypeName(configuredByAttributes.get("value"));
        TypeInfo parent = service.superTypeInfo().orElse(null);
        boolean hasParent = (parent != null);
        TypeName serviceTypeName = service.typeName();
        TypeName parentServiceTypeName = (hasParent) ? parent.typeName() : null;
        TypeName activatorImplTypeName = activatorCreator().toActivatorImplTypeName(serviceTypeName);
        TypeName genericCB = createFromGenericDeclaration("CB");
        TypeName genericExtendsCB = createFromGenericDeclaration("CB extends " + configBeanType.name());

        if (hasParent && findFirst(ConfiguredBy.class, parent.annotations()).isEmpty()) {
            // we treat this as a regular configured service, since its parent is NOT a configured service
            hasParent = false;
            parentServiceTypeName = null;
        }

        if (hasParent) {
            // we already know our parent, but we need to morph it with our activator and new CB reference
            TypeName parentActivatorImplTypeName = ActivatorCreatorProvider.instance()
                    .toActivatorImplTypeName(parentServiceTypeName);
            parentServiceTypeName = toBuilder(parentActivatorImplTypeName)
                    .typeArguments(List.of(genericCB))
                    .build();
        } else {
            List<TypeName> typeArgs = List.of(serviceTypeName, genericCB);
            parentServiceTypeName = create(AbstractConfiguredServiceProvider.class).toBuilder()
                    .typeArguments(typeArgs)
                    .build();
        }

        List<String> extraCodeGen = createExtraCodeGen(activatorImplTypeName, configBeanType, hasParent, configuredByAttributes);

        boolean accepted = services.addParentServiceType(serviceTypeName, parentServiceTypeName, Optional.of(true));
        assert (accepted);
        services.addActivatorGenericDecl(serviceTypeName, "<" + genericExtendsCB.fqName() + ">");
        extraCodeGen.forEach(fn -> services.addExtraCodeGen(serviceTypeName, fn));

        List<String> extraActivatorClassComments = createExtraActivatorClassComments();
        extraActivatorClassComments.forEach(fn -> services.addExtraActivatorClassComments(serviceTypeName, fn));
    }

}
