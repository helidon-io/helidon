/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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
package io.helidon.security.abac.role;

import java.lang.reflect.Method;
import java.util.List;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.metadata.reflection.AnnotationFactory;
import io.helidon.metadata.reflection.TypedElementFactory;
import io.helidon.security.providers.abac.AbacProvider;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

import jakarta.annotation.security.PermitAll;

/**
 * Implementation of {@link AnnotationAnalyzer} which checks for {@link PermitAll} annotation if
 * authentication is needed or not.
 */
@Weight(Weighted.DEFAULT_WEIGHT) // Helidon service loader loaded and ordered
public class RoleAnnotationAnalyzer implements AnnotationAnalyzer {

    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
        return analyze(TypeName.create(maybeAnnotated), AnnotationFactory.create(maybeAnnotated));
    }

    @Override
    public AnalyzerResponse analyze(Method maybeAnnotated, AnalyzerResponse previousResponse) {
        // these methods must be implemented, as otherwise the default behavior would occur
        // will be removed in next major version
        return analyze(TypeName.create(maybeAnnotated.getDeclaringClass()),
                       TypedElementFactory.create(maybeAnnotated),
                       previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated, AnalyzerResponse previousResponse) {
        // these methods must be implemented, as otherwise the default behavior would occur
        // will be removed in next major version
        return analyze(TypeName.create(maybeAnnotated), AnnotationFactory.create(maybeAnnotated), previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(TypeName applicationType, List<Annotation> annotations) {
        return analyze(annotations, AnalyzerResponse.abstain());
    }

    @Override
    public AnalyzerResponse analyze(TypeName endpointType, List<Annotation> annotations, AnalyzerResponse previousResponse) {
        return analyze(annotations, previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(TypeName typeName, TypedElementInfo method, AnalyzerResponse previousResponse) {
        return analyze(method.annotations(), previousResponse);
    }

    private static AnalyzerResponse analyze(List<Annotation> annotations, AnalyzerResponse previousResponse) {
        if (hasAnnotation(annotations,
                          AbacProvider.PERMIT_ALL_JAKARTA_TYPE,
                          AbacProvider.PERMIT_ALL_JAVAX_TYPE,
                          RoleValidator.PermitAll.TYPE)) {
            // permit all wins
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.OPTIONAL)
                    .authorizeResponse(Flag.OPTIONAL)
                    .build();
        }

        if (hasAnnotation(annotations,
                          AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE,
                          AbacProvider.ROLES_ALLOWED_JAVAX_TYPE,
                          RoleValidator.RolesContainer.TYPE,
                          RoleValidator.Roles.TYPE)) {
            // when roles allowed are defined, we require authentication (roles allowed is handled by authentication)
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }

        return previousResponse;
    }

    private static boolean hasAnnotation(List<Annotation> annotations, TypeName... typeNames) {
        for (TypeName typeName : typeNames) {
            if (Annotations.findFirst(typeName, annotations).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
