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
package io.helidon.tests.integration.harness;

import java.lang.reflect.Parameter;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.suite.api.Suite;

/**
 * A {@link ParameterResolver} to support {@link Suite}.
 */
public class SuiteParameterResolver implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        return SuiteFinder.findSuite(extensionContext)
                .map(suite -> suite.parameter(paramType).isPresent())
                .orElse(false);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) throws ParameterResolutionException {

        Parameter parameter = parameterContext.getParameter();
        Class<?> paramType = parameter.getType();
        return SuiteFinder.findSuite(extensionContext)
                .flatMap(suite -> suite.parameter(paramType))
                .orElseThrow(() -> new ParameterResolutionException("Unable to resolve parameter: " + parameter));
    }
}
