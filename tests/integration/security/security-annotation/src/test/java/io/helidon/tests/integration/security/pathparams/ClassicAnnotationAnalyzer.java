/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.pathparams;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;
import io.helidon.tests.integration.security.annotation.ClassicRootPublicResource;
import io.helidon.tests.integration.security.annotation.ClassicRootSecuredResource;
import io.helidon.tests.integration.security.annotation.ClassicRootSecurityBase;
import io.helidon.tests.integration.security.annotation.InheritedHandlerSecurityResource.ClassicAuthenticationRequired;
import io.helidon.tests.integration.security.annotation.InheritedHandlerSecurityResource.ClassicAnalyzerHandler;

/**
 * Analyzer verifying the callback topology retained for analyzers that do not opt in to expanded analysis.
 */
@Weight(300) // Runs before TypedMethodAnnotationAnalyzer to verify mixed classic and expanded ordering.
public class ClassicAnnotationAnalyzer implements AnnotationAnalyzer {
    private static final String SECURED_SUBRESOURCE_TYPE = ClassicAnalyzerHandler.class.getSimpleName();
    private static final String ORDERED_PUBLIC_ROOT_TYPE = ClassicRootPublicResource.class.getCanonicalName();
    private static final String SECURED_ROOT_TYPE = ClassicRootSecuredResource.class.getCanonicalName();
    private static final TypeName AUTHENTICATION_REQUIRED = TypeName.create(ClassicAuthenticationRequired.class);
    private static final List<String> ANALYSES = new CopyOnWriteArrayList<>();
    private static final RootSecurityMarker ROOT_SECURITY_MARKER = new RootSecurityMarker();

    static void reset() {
        ANALYSES.clear();
    }

    static List<String> analyses() {
        return List.copyOf(ANALYSES);
    }

    @Override
    public AnalyzerResponse analyze(Class<?> applicationClass) {
        return AnalyzerResponse.abstain();
    }

    @Override
    public AnalyzerResponse analyze(TypeName endpointType,
                                    List<Annotation> annotations,
                                    AnalyzerResponse previousResponse) {
        ANALYSES.add("class:" + endpointType.fqName());
        if (endpointType.fqName().endsWith('.' + SECURED_SUBRESOURCE_TYPE)) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        if (ORDERED_PUBLIC_ROOT_TYPE.equals(endpointType.fqName())) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        if (SECURED_ROOT_TYPE.equals(endpointType.fqName())) {
            return AnalyzerResponse.builder(previousResponse)
                    .register(ROOT_SECURITY_MARKER)
                    .build();
        }
        return AnalyzerResponse.abstain(previousResponse);
    }

    @Override
    @SuppressWarnings("removal")
    public AnalyzerResponse analyze(Method method, AnalyzerResponse previousResponse) {
        ANALYSES.add("reflection-method:" + method.getDeclaringClass().getName() + "#" + method.getName());
        if (ClassicRootSecurityBase.class.equals(method.getDeclaringClass()) && containsRootSecurity(previousResponse)) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        return AnalyzerResponse.abstain(previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(TypeName typeName,
                                    TypedElementInfo method,
                                    AnalyzerResponse previousResponse) {
        ANALYSES.add("metadata-method:" + typeName.fqName() + "#" + method.elementName());
        if (method.hasAnnotation(AUTHENTICATION_REQUIRED)) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        return AnalyzerResponse.abstain(previousResponse);
    }

    private static boolean containsRootSecurity(AnalyzerResponse response) {
        AnalyzerResponse current = response;
        while (current != null) {
            if (current.registry().getInstance(RootSecurityMarker.class).isPresent()) {
                return true;
            }
            current = current.parent().orElse(null);
        }
        return false;
    }

    private static final class RootSecurityMarker {
    }
}
