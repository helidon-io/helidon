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

package io.helidon.pico.config.services.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.pico.config.spi.ConfigBuilderValidator;
import io.helidon.pico.PicoException;

import jakarta.inject.Singleton;

import static java.lang.System.Logger.Level.*;

/**
 * The default implementation for {@link io.helidon.pico.config.spi.ConfigBuilderValidator}.
 */
@Singleton
public class DefaultConfigBuilderValidator implements ConfigBuilderValidator {

    @Override
    public ValidationRound createValidationRound(Object builder, Object receiver, Class<?> configBeanType) {
        assert (Objects.nonNull(builder));
        assert (Objects.nonNull(configBeanType));

        if (Objects.isNull(receiver)) {
            return null;
        }

        return new DefaultValidation(receiver, configBeanType);
    }


    static class DefaultValidation implements ValidationRound {
        private final Object receiver;
        private final Class<?> configBeanType;
        private final List<ValidationIssue> issues = new LinkedList<>();
        private boolean finished;

        DefaultValidation(Object receiver, Class<?> configBeanType) {
            this.receiver = receiver;
            this.configBeanType = Objects.requireNonNull(configBeanType);
        }

        @Override
        public List<ValidationIssue> getIssues() {
            return Collections.unmodifiableList(issues);
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public ValidationRound validate(String attributeName,
                                        Supplier<?> valueSupplier,
                                        Class<?> cbType,
                                        Map<String, Object> meta) {
            if (Objects.isNull(receiver) || cbType.isPrimitive() || cbType.getName().startsWith("java.lang.")) {
                return this;
            }

            Object val = valueSupplier.get();
            if (Objects.isNull(val)) {
                return this;
            }

            Collection<?> values = extractValues(val, cbType);
            if (values.contains(receiver)) {
                issues.add(new ValidationIssue(Severity.ERROR, attributeName,
                                               "receiver can't be injected with itself"));
            }
            return this;
        }

        protected Collection<?> extractValues(Object rawVal, Class<?> cbType) {
            if (Objects.isNull(rawVal)) {
                return Collections.emptyList();
            }

            if (Optional.class.equals(rawVal.getClass())) {
                Optional<?> val = (Optional<?>) rawVal;
                if (val.isEmpty()) {
                    return Collections.emptyList();
                }

                rawVal = val.get();
            }

            if (rawVal instanceof Collection) {
                return (Collection<?>) rawVal;
            }

            if (rawVal instanceof Map) {
                return extractValues(((Map) rawVal).values(), null);
            }

            if (Objects.nonNull(cbType) && cbType.isArray()) {
                if (Objects.nonNull(cbType.getComponentType()) && !cbType.getComponentType().isPrimitive()) {
                    return List.of((Object[]) rawVal);
                }
            }

            return Collections.singletonList(rawVal);
        }

        @Override
        public ValidationRound finish(boolean throwIfErrors) {
            assert (!finished) : "already finished";
            finished = true;

            logIssues();
            if (throwIfErrors && hasErrors()) {
                throw new PicoException("Validation failed for config bean of type  "
                                                + configBeanType.getName() + ":\n" + toDescription(issues));
            }
            return this;
        }

        void logIssues() {
            if (!hasIssues()) {
                return;
            }

            System.Logger logger = System.getLogger(DefaultConfigBuilderValidator.class.getName());
            issues.forEach(issue -> logger.log(issue.getSeverity() == Severity.ERROR ? ERROR : WARNING, issue));
        }

        static String toDescription(List<ValidationIssue> issues) {
            StringBuilder builder = new StringBuilder();
            issues.forEach(issue -> {
                builder.append("* ").append(issue.getSeverity()).append(": ").append(issue.getAttributeName());
                builder.append(": ").append(issue.getMessage());
            });
            return builder.toString();
        }
    }

}
