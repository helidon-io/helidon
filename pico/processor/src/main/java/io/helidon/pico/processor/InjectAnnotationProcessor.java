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

package io.helidon.pico.processor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import io.helidon.common.types.TypeName;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.DependencyInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.services.Dependencies;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeNames;
import io.helidon.pico.tools.TypeTools;

import static io.helidon.pico.tools.TypeTools.createQualifierAndValueSet;
import static io.helidon.pico.tools.TypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.TypeTools.isStatic;
import static io.helidon.pico.tools.TypeTools.toAccess;

/**
 * Handles {@code @Inject} annotations on fields and methods.
 *
 * @deprecated
 */
public class InjectAnnotationProcessor extends BaseAnnotationProcessor<Dependencies.BuilderContinuation> {
    private static final Set<String> SUPPORTED_TARGETS = Set.of(TypeNames.JAKARTA_INJECT,
                                                                TypeNames.JAVAX_INJECT);

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public InjectAnnotationProcessor() {
    }

    @Override
    protected Set<String> annoTypes() {
        return SUPPORTED_TARGETS;
    }

    @Override
    protected Set<String> contraAnnotations() {
        return Set.of(TypeNames.PICO_CONFIGURED_BY);
    }

    @Override
    int doBulkInner(
            Set<? extends Element> typesToProcess,
            TypeName typeName,
            Dependencies.BuilderContinuation builder) {
        if (typesToProcess.isEmpty()) {
            return 0;
        }

        assert (Objects.isNull(typeName));
        assert (Objects.isNull(builder));

        Set<TypeName> serviceTypeNames = typesToProcess.stream()
                .map(element -> createTypeNameFromElement(element.getEnclosingElement()).orElseThrow())
                .collect(Collectors.toSet());
        assert (!serviceTypeNames.isEmpty());
        for (TypeName serviceTypeName : serviceTypeNames) {
            builder = Dependencies.builder(serviceTypeName.name());
            int ctorCount = super.doBulkInner(typesToProcess, serviceTypeName, builder);
            if (ctorCount > 1) {
                throw new ToolsException("there can be max 1 injectable constructor for " + serviceTypeName);
            }
            DependenciesInfo dependencies = builder.build();
            servicesToProcess().addDependencies(dependencies);
            maybeSetBasicsForServiceType(serviceTypeName, null);
        }

        return 0;
    }

    @Override
    void doInner(
            String serviceTypeName,
            VariableElement varType,
            Dependencies.BuilderContinuation continuation,
            String elemName,
            int elemArgs,
            Integer elemOffset,
            InjectionPointInfo.ElementKind elemKind,
            InjectionPointInfo.Access access,
            Boolean isStaticAlready) {
        boolean isStatic = Objects.nonNull(isStaticAlready) && isStaticAlready;
        if (access == null) {
            if (varType.getKind() != ElementKind.FIELD) {
                throw new ToolsException("unsupported element kind " + varType.getEnclosingElement()
                                                 + "." + varType + " with " + varType.getKind());
            }

            access = toAccess(varType);
            elemKind = InjectionPointInfo.ElementKind.FIELD;
            isStatic = isStatic(varType);
        }

        serviceTypeName = (serviceTypeName != null) ? serviceTypeName : varType.getEnclosingElement().toString();
        elemName = (elemName != null) ? elemName : varType.getSimpleName().toString();

        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        String varTypeName = TypeTools.extractInjectionPointTypeInfo(varType, isProvider, isList, isOptional);
        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(varType.getAnnotationMirrors());

        assert (Objects.nonNull(elemKind) && Objects.nonNull(elemName));
        continuation = continuation.add(serviceTypeName, elemName, varTypeName, elemKind, elemArgs, access)
                .elemOffset(elemOffset)
                .qualifiers(qualifiers)
                .providerWrapped(isProvider.get())
                .listWrapped(isList.get())
                .optionalWrapped(isOptional.get())
                .staticDeclaration(isStatic);
        DependencyInfo ipInfo = continuation.commitLastDependency().orElseThrow();
        debug("dependency for " + varType + " was " + ipInfo);
    }

    @Override
    void doInner(
            ExecutableElement method,
            Dependencies.BuilderContinuation builder) {
        if (method.getKind() != ElementKind.METHOD
                && method.getKind() != ElementKind.CONSTRUCTOR) {
            throw new ToolsException("unsupported element kind " + method.getEnclosingElement()
                                             + "." + method + " with " + method.getKind());
        }

        final InjectionPointInfo.Access access = toAccess(method);
        final boolean isStatic = isStatic(method);

        List<? extends VariableElement> params = method.getParameters();
        if (params.isEmpty() && method.getKind() != ElementKind.CONSTRUCTOR) {
            throw new ToolsException("unsupported element kind " + method.getEnclosingElement()
                                             + "." + method + " with " + method.getKind());
        }

        String serviceTypeName = method.getEnclosingElement().toString();
        String methodTypeName = (ElementKind.CONSTRUCTOR == method.getKind())
                ? InjectionPointInfo.CONSTRUCTOR
                : method.getSimpleName().toString();
        InjectionPointInfo.ElementKind elemKind = (ElementKind.CONSTRUCTOR == method.getKind())
                ? InjectionPointInfo.ElementKind.CONSTRUCTOR : InjectionPointInfo.ElementKind.METHOD;
        if (!params.isEmpty()) {
            int elemOffset = 0;
            for (VariableElement varType : params) {
                doInner(serviceTypeName, varType, builder, methodTypeName,
                        params.size(), ++elemOffset, elemKind, access, isStatic);
            }
        }
    }

    @Override
    public void doInner(
            TypeElement type,
            Dependencies.BuilderContinuation builder) {
        throw new IllegalStateException();  // should never be here
    }

}
