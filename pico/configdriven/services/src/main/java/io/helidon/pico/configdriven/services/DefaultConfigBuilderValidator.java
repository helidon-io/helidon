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

package io.helidon.pico.configdriven.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.config.spi.ConfigBeanBuilderValidator;
import io.helidon.pico.PicoException;

/**
 * The default implementation for {@link ConfigBeanBuilderValidator}.
 */
class DefaultConfigBuilderValidator<CBB> implements ConfigBeanBuilderValidator<CBB> {

    DefaultConfigBuilderValidator() {
    }

    /**
     * Creates a validation round for all the config bean attributes of the provided config bean.
     *
     * @param builder               the config builder instance
     * @param configBeanBuilderType the config bean type
     * @return the validation round that can be used for attribute level validation
     */
    @Override
    public ValidationRound createValidationRound(
            CBB builder,
            // Receiver?
            Class<CBB> configBeanBuilderType) {
        assert (Objects.nonNull(builder));
        assert (Objects.nonNull(configBeanBuilderType));

        return new DefaultValidation(configBeanBuilderType);
    }

    static class DefaultValidation implements ValidationRound {
        private final Class<?> configBeanType;
        private final List<ValidationIssue> issues = new ArrayList<>();
        private boolean finished;

        DefaultValidation(
                Class<?> configBeanType) {
            this.configBeanType = Objects.requireNonNull(configBeanType);
        }

        @Override
        public List<ValidationIssue> issues() {
            return List.copyOf(issues);
        }

        @Override
        public boolean isCompleted() {
            return finished;
        }

        @Override
        public ValidationRound validate(String attributeName,
                                        Supplier<?> valueSupplier,
                                        Class<?> cbType,
                                        Map<String, Object> meta) {
            if (cbType.isPrimitive() || cbType.getName().startsWith("java.lang.")) {
                return this;
            }

            Object val = valueSupplier.get();
            if (Objects.isNull(val)) {
                return this;
            }

            // note to self: _todo:
//            Collection<?> values = extractValues(val, cbType);
//            if (values.contains(receiver)) {
//                issues.add(new ValidationIssue(Severity.ERROR, attributeName,
//                                               "receiver can't be injected with itself"));
//            }
            return this;
        }

        Collection<?> extractValues(
                Object rawVal,
                Class<?> cbType) {
            if (rawVal == null) {
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

            if (cbType != null && cbType.isArray()) {
                if (cbType.getComponentType() != null && !cbType.getComponentType().isPrimitive()) {
                    return List.of((Object[]) rawVal);
                }
            }

            return List.of(rawVal);
        }

        @Override
        public ValidationRound finish(
                boolean throwIfErrors) {
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
            issues.forEach(issue -> logger.log(issue.severity() == Severity.ERROR
                                                       ? System.Logger.Level.ERROR : System.Logger.Level.WARNING, issue));
        }

        static String toDescription(
                List<ValidationIssue> issues) {
            StringBuilder builder = new StringBuilder();
            issues.forEach(issue -> {
                builder.append("* ").append(issue.severity()).append(": ").append(issue.attributeName());
                builder.append(": ").append(issue.message());
            });

            return builder.toString();
        }

    }

}
