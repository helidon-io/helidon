/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tests.testing.junit5;

import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;

/**
 * Condition implementation of {@link EnabledIfParameter}.
 */
final class EnabledIfParameterCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return context.getElement()
                .flatMap(e -> Optional.ofNullable(e.getAnnotation(EnabledIfParameter.class)))
                .map(a -> context.getConfigurationParameter(a.key())
                        .map(v -> v.equals(a.value()) ?
                                enabled("parameter match: %s".formatted(a.key())) :
                                disabled("parameter mismatch: %s!=%s".formatted(a.value(), v)))
                        .orElse(disabled("parameter not found: %s".formatted(a.key()))))
                .orElse(enabled("@EnabledIfParameter not present"));
    }
}
