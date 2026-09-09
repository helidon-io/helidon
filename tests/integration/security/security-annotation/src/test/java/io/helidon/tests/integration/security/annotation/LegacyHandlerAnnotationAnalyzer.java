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

import io.helidon.security.providers.common.spi.AnnotationAnalyzer;
import io.helidon.tests.integration.security.annotation.InheritedHandlerSecurityResource.LegacyAuthenticationDisabled;
import io.helidon.tests.integration.security.annotation.InheritedHandlerSecurityResource.LegacyAuthenticationRequired;

/**
 * Legacy reflection-based analyzer used to verify inherited handler annotation precedence.
 */
@SuppressWarnings("removal")
public class LegacyHandlerAnnotationAnalyzer implements AnnotationAnalyzer {
    private static final AnalysisMarker ANALYSIS_MARKER = new AnalysisMarker();

    @Override
    public boolean supportsExpandedEndpointAnalysis() {
        return true;
    }

    @Override
    public AnalyzerResponse analyze(Class<?> applicationClass) {
        return AnalyzerResponse.abstain();
    }

    @Override
    public AnalyzerResponse analyze(Class<?> resourceClass, AnalyzerResponse previousResponse) {
        if (containsAnalysisMarker(previousResponse)) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.FORBIDDEN)
                    .build();
        }
        if (resourceClass.isAnnotationPresent(LegacyAuthenticationRequired.class)) {
            return AnalyzerResponse.builder(previousResponse)
                    .register(ANALYSIS_MARKER)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        if (resourceClass.isAnnotationPresent(LegacyAuthenticationDisabled.class)) {
            return AnalyzerResponse.builder(previousResponse)
                    .register(ANALYSIS_MARKER)
                    .authenticationResponse(Flag.FORBIDDEN)
                    .build();
        }
        return AnalyzerResponse.abstain(previousResponse);
    }

    private static boolean containsAnalysisMarker(AnalyzerResponse response) {
        AnalyzerResponse current = response;
        while (current != null) {
            if (current.registry().getInstance(AnalysisMarker.class).isPresent()) {
                return true;
            }
            current = current.parent().orElse(null);
        }
        return false;
    }

    private static final class AnalysisMarker {
    }
}
