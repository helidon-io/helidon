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
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

/**
 * Expanded metadata analyzer used to verify the application callback contract.
 */
public class ExpandedApplicationSecurityAnnotationAnalyzer implements AnnotationAnalyzer {
    private static final TypeName TEST_APPLICATION = TypeName.create(ApplicationSecurityAnnotationTest.TestApplication.class);
    private static final AtomicReference<List<Annotation>> APPLICATION_ANNOTATIONS = new AtomicReference<>(List.of());

    static void reset() {
        APPLICATION_ANNOTATIONS.set(List.of());
    }

    static List<TypeName> annotationTypes() {
        return APPLICATION_ANNOTATIONS.get()
                .stream()
                .map(Annotation::typeName)
                .toList();
    }

    @Override
    public boolean supportsExpandedEndpointAnalysis() {
        return true;
    }

    @Override
    public AnalyzerResponse analyze(TypeName applicationType, List<Annotation> annotations) {
        if (TEST_APPLICATION.equals(applicationType)) {
            APPLICATION_ANNOTATIONS.set(List.copyOf(annotations));
        }
        return AnalyzerResponse.abstain();
    }

    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
        return AnalyzerResponse.abstain();
    }
}
