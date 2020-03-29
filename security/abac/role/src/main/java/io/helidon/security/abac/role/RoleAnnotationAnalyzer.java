/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.annotation.Priority;
import javax.annotation.security.PermitAll;

import io.helidon.common.Prioritized;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

/**
 * Implementation of {@link AnnotationAnalyzer} which checks for {@link PermitAll} annotation if
 * authentication is needed or not.
 */
@Priority(Prioritized.DEFAULT_PRIORITY)
public class RoleAnnotationAnalyzer implements AnnotationAnalyzer {

    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
        return AnalyzerResponse.abstain();
    }

    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated, AnalyzerResponse previousResponse) {
        return analyze(maybeAnnotated.getAnnotation(PermitAll.class), previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(Method maybeAnnotated, AnalyzerResponse previousResponse) {
        return analyze(maybeAnnotated.getAnnotation(PermitAll.class), previousResponse);

    }

    private static AnalyzerResponse analyze(PermitAll permitAll, AnalyzerResponse previousResponse) {
        if (permitAll == null) {
            return AnalyzerResponse.builder(previousResponse)
                    .build();
        }

        return AnalyzerResponse.builder(previousResponse)
                .authenticationResponse(Flag.OPTIONAL)
                .authorizeResponse(Flag.OPTIONAL)
                .build();
    }
}
