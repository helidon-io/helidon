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

package io.helidon.validation.validators;

import java.util.List;
import java.util.regex.Pattern;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.NamedByType(Validation.String.Pattern.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringPatternValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int flags = 0;
        String regexp = constraintAnnotation.value().orElseThrow();
        var annotationFlags = constraintAnnotation.enumValues("flags",
                                                              Validation.String.Pattern.Flag.class)
                .orElseGet(List::of);

        for (Validation.String.Pattern.Flag annotationFlag : annotationFlags) {
            flags |= annotationFlag.value();
        }

        Pattern pattern = Pattern.compile(regexp, flags);

        int finalFlags = flags;

        return new PatternValidator(constraintAnnotation,
                                    pattern,
                                    "does not match pattern \"" + regexp + "\" with flags " + finalFlags);
    }

    private static class PatternValidator extends BaseValidator {
        private PatternValidator(Annotation annotation, Pattern pattern, String defaultMessage) {
            super(annotation,
                  defaultMessage,
                  it -> validate(it, pattern));
        }

        private static boolean validate(Object value, Pattern pattern) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            return pattern.matcher(chars).matches();
        }
    }
}
