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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;
import io.helidon.tests.integration.security.annotation.ClassSecurityCacheResource;

/**
 * Counts analysis of a dedicated resource and its methods.
 */
public class ClassSecurityCacheAnnotationAnalyzer implements AnnotationAnalyzer {
    private static final TypeName RESOURCE_TYPE = TypeName.create(ClassSecurityCacheResource.class);
    private static final AtomicInteger CLASS_ANALYSES = new AtomicInteger();
    private static final AtomicInteger METHOD_ANALYSES = new AtomicInteger();

    static void reset() {
        CLASS_ANALYSES.set(0);
        METHOD_ANALYSES.set(0);
    }

    static int classAnalyses() {
        return CLASS_ANALYSES.get();
    }

    static int methodAnalyses() {
        return METHOD_ANALYSES.get();
    }

    @Override
    public boolean supportsExpandedEndpointAnalysis() {
        return true;
    }

    @Override
    public AnalyzerResponse analyze(Class<?> applicationClass) {
        return AnalyzerResponse.abstain();
    }

    @Override
    public AnalyzerResponse analyze(TypeName endpointType,
                                    List<Annotation> annotations,
                                    AnalyzerResponse previousResponse) {
        if (RESOURCE_TYPE.equals(endpointType)) {
            CLASS_ANALYSES.incrementAndGet();
        }
        return AnalyzerResponse.abstain(previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(TypeName typeName,
                                    TypedElementInfo method,
                                    AnalyzerResponse previousResponse) {
        if (RESOURCE_TYPE.equals(typeName)) {
            METHOD_ANALYSES.incrementAndGet();
        }
        return AnalyzerResponse.abstain(previousResponse);
    }

}
