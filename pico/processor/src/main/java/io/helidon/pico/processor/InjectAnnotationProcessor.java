/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
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

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.spi.ext.Dependency;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.processor.JavaxTypeTools;
import io.helidon.pico.tools.processor.TypeTools;

import jakarta.inject.Inject;

import static io.helidon.pico.tools.processor.TypeTools.createQualifierAndValueSet;
import static io.helidon.pico.tools.processor.TypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.processor.TypeTools.isStatic;
import static io.helidon.pico.tools.processor.TypeTools.toAccess;

/**
 * Looks for @Inject type field & method annotations.
 */
public class InjectAnnotationProcessor extends BaseAnnotationProcessor<Dependencies.BuilderContinuation> {

    private static final Set<Class<? extends Annotation>> SUPPORTED_TARGETS;
    private static Class<? extends Annotation> javaxInjectType;
    static {
        SUPPORTED_TARGETS = new HashSet<>();
        SUPPORTED_TARGETS.add(Inject.class);
        addJavaxTypes(SUPPORTED_TARGETS);
    }

    private static void addJavaxTypes(Set<Class<? extends Annotation>> supportedTargets) {
        if (Objects.nonNull(javaxInjectType)) {
            return;
        }

        try {
            javaxInjectType = JavaxTypeTools.INSTANCE.get()
                    .loadAnnotationClass(TypeTools.oppositeOf(Inject.class.getName()));
            if (Objects.nonNull(javaxInjectType)) {
                supportedTargets.add(javaxInjectType);
            }
        } catch (Throwable t) {
            // expected
        }
    }

    @Override
    protected Set<Class<? extends Annotation>> getAnnoTypes() {
        return SUPPORTED_TARGETS;
    }

    @Override
    public Set<String> getContraAnnotations() {
        return Collections.singleton(CONFIGURED_BY_TYPENAME);
    }

    @Override
    public int doBulkInner(Set<? extends Element> typesToProcess,
                           TypeName typeName,
                           Dependencies.BuilderContinuation builder) {
        if (typesToProcess.isEmpty()) {
            return 0;
        }

        assert (Objects.isNull(typeName));
        assert (Objects.isNull(builder));

        Set<TypeName> serviceTypeNames = typesToProcess.stream()
                .map(element -> createTypeNameFromElement(element.getEnclosingElement()))
                .collect(Collectors.toSet());
        assert (!serviceTypeNames.isEmpty());

        for (TypeName serviceTypeName : serviceTypeNames) {
            builder = Dependencies.builder().forServiceTypeName(serviceTypeName.name());
            int ctorCount = super.doBulkInner(typesToProcess, serviceTypeName, builder);
            if (ctorCount > 1) {
                throw new ToolsException("there can be max 1 injectable constructor for " + serviceTypeName);
            }
            Dependencies dependencies = builder.build().build();
            services.addDependencies(dependencies);
            maybeSetBasicsForServiceType(serviceTypeName, null);
        }

        return 0;
    }

    @Override
    public void doInner(String serviceTypeName,
                        VariableElement varType,
                        Dependencies.BuilderContinuation continuation,
                        String elemName,
                        int elemArgs,
                        Integer elemOffset,
                        InjectionPointInfo.ElementKind elemKind,
                        InjectionPointInfo.Access access,
                        Boolean isStaticAlready) {
        boolean isStatic = Objects.nonNull(isStaticAlready) && isStaticAlready;
        if (Objects.isNull(access)) {
            if (varType.getKind() != ElementKind.FIELD) {
                throw new ToolsException("unsupported element kind " + varType.getEnclosingElement()
                                                 + "." + varType + " with " + varType.getKind());
            }

            access = toAccess(varType);
            elemKind = InjectionPointInfo.ElementKind.FIELD;
            isStatic = isStatic(varType);
        }

        serviceTypeName = (Objects.nonNull(serviceTypeName)) ? serviceTypeName : varType.getEnclosingElement().toString();
        elemName = Objects.nonNull(elemName) ? elemName : varType.getSimpleName().toString();

        AtomicReference<Boolean> isProvider = new AtomicReference<>();
        AtomicReference<Boolean> isOptional = new AtomicReference<>();
        AtomicReference<Boolean> isList = new AtomicReference<>();
        String varTypeName = TypeTools.extractInjectionPointTypeInfo(varType, isProvider, isList, isOptional);
        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(varType.getAnnotationMirrors());

        assert (Objects.nonNull(elemKind) && Objects.nonNull(elemName));
        continuation = continuation.add(serviceTypeName, elemName, varTypeName, elemKind, elemArgs, access)
                .elemOffset(elemOffset)
                .qualifiers(qualifiers)
                .setIsProviderWrapped(isProvider.get())
                .setIsListWrapped(isList.get())
                .setIsOptionalWrapped(isOptional.get())
                .setIsStatic(isStatic);
        Dependency<?> ipInfo = Objects.requireNonNull(continuation.commitLastDependency());
        debug("dependency for " + varType + " was " + ipInfo);
    }

    @Override
    public void doInner(ExecutableElement method,
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
                ? InjectionPointInfo.CTOR
                : method.getSimpleName().toString();
        InjectionPointInfo.ElementKind elemKind = (ElementKind.CONSTRUCTOR == method.getKind())
                ? InjectionPointInfo.ElementKind.CTOR : InjectionPointInfo.ElementKind.METHOD;
        if (!params.isEmpty()) {
            int elemOffset = 0;
            for (VariableElement varType : params) {
                doInner(serviceTypeName, varType, builder, methodTypeName,
                        params.size(), ++elemOffset, elemKind, access, isStatic);
            }
        }
    }

    @Override
    public void doInner(TypeElement type, Dependencies.BuilderContinuation builder) {
        throw new IllegalStateException();  // should never be here
    }

}
