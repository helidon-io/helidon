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

package io.helidon.tests.integration.security.annotation;

import java.util.List;

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

/**
 * Metadata-based analyzer used to verify root resource method dispatch.
 */
@Weight(200)
public class TypedMethodAnnotationAnalyzer implements AnnotationAnalyzer {
    private static final TypeName AUTHENTICATION_REQUIRED =
            TypeName.create(AdminResource.TypedAuthenticationRequired.class);
    private static final TypeName INTERFACE_RESOURCE = TypeName.create(InterfaceSecurityResource.class);
    private static final TypeName INTERFACE_RESOURCE_IMPL = TypeName.create(InterfaceSecurityResourceImpl.class);
    private static final TypeName ORDERED_PUBLIC_ROOT = TypeName.create(ClassicRootPublicResource.class);

    @Override
    public boolean supportsExpandedEndpointAnalysis() {
        return true;
    }

    @Override
    public AnalyzerResponse analyze(Class<?> applicationClass) {
        return AnalyzerResponse.abstain();
    }

    @Override
    public AnalyzerResponse analyze(TypeName typeName,
                                    List<Annotation> annotations,
                                    AnalyzerResponse previousResponse) {
        if (ORDERED_PUBLIC_ROOT.fqName().equals(typeName.fqName())) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.FORBIDDEN)
                    .build();
        }
        return AnalyzerResponse.abstain(previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(TypeName typeName,
                                    TypedElementInfo method,
                                    AnalyzerResponse previousResponse) {
        if (method.hasAnnotation(AUTHENTICATION_REQUIRED)) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        if ("implementationAnalyzerRequired".equals(method.elementName())) {
            if (INTERFACE_RESOURCE.equals(typeName)) {
                return AnalyzerResponse.builder(previousResponse)
                        .authorizeResponse(Flag.FORBIDDEN)
                        .build();
            }
            if (INTERFACE_RESOURCE_IMPL.equals(typeName)) {
                return AnalyzerResponse.builder(previousResponse)
                        .authorizeResponse(Flag.REQUIRED)
                        .authorizer("abac")
                        .build();
            }
        }
        if ("implementationAnalyzerForbidden".equals(method.elementName())) {
            if (INTERFACE_RESOURCE.equals(typeName)) {
                return AnalyzerResponse.builder(previousResponse)
                        .authorizeResponse(Flag.REQUIRED)
                        .authorizer("abac")
                        .build();
            }
            if (INTERFACE_RESOURCE_IMPL.equals(typeName)) {
                return AnalyzerResponse.builder(previousResponse)
                        .authorizeResponse(Flag.FORBIDDEN)
                        .build();
            }
        }
        return AnalyzerResponse.abstain(previousResponse);
    }
}
