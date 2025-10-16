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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidationException;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.NamedByType(Validation.Collection.Size.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class CollectionSizeValidationProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int minLength = constraintAnnotation.intValue("min").orElse(0);
        int maxLength = constraintAnnotation.intValue("value").orElse(Integer.MAX_VALUE);

        String message = message(minLength, maxLength);

        if (type.array()) {
            return new CollectionSizeValidator(constraintAnnotation,
                                     message,
                                     it -> {
                                         var len = Array.getLength(it);
                                         return len >= minLength && len <= maxLength;
                                     });
        } else {
            // we do not have the option to correctly guess what type we have, so instance check it is....
            return new CollectionSizeValidator(constraintAnnotation,
                                     message,
                                     it -> {
                                         var len = len(it);
                                         return len >= minLength && len <= maxLength;
                                     });
        }
    }

    private static int len(Object object) {
        if (object instanceof Collection<?> c) {
            return c.size();
        }
        if (object instanceof Map<?, ?> m) {
            return m.size();
        }
        if (object.getClass().isArray()) {
            return Array.getLength(object);
        }

        throw new ValidationException(
                "Collection size constraint is only valid on an array, collection, or a "
                        + "map.");
    }

    private static String message(int minLength, int maxLength) {
        if (maxLength == Integer.MAX_VALUE && minLength == 0) {
            // cannot fail
            return "";
        }
        if (maxLength == Integer.MAX_VALUE) {
            return "size (%d) should be at least " + minLength;
        }
        if (minLength == 0) {
            return "size (%d) should be at most " + maxLength;
        }
        return "size (%d) should be between " + minLength + " and " + maxLength;
    }

    private static class CollectionSizeValidator extends BaseValidator {
        protected CollectionSizeValidator(Annotation annotation, String defaultMessage, Predicate<Object> check) {
            super(annotation, defaultMessage, check);
        }

        @Override
        protected Object convertValue(Object object) {
            return len(object);
        }
    }
}
