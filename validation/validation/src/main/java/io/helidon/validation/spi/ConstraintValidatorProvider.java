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

package io.helidon.validation.spi;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;

/**
 * A provider used to create validators for elements annotated with some {@link io.helidon.validation.Validation.Constraint}
 * annotation.
 * <p>
 * The provider must be named with the supported annotation fully qualified type name.
 * Service registry is then used to discover an instance to handle the validation.
 * All built-in validators have lower than default {@link io.helidon.common.Weight}.
 */
@Service.Contract
public interface ConstraintValidatorProvider {
    /**
     * Create a validator for the given annotation and type.
     *
     * @param typeName             type of the annotated element
     * @param constraintAnnotation constraint annotation of the annotated element
     * @return validator to validate instances of the annotated element
     */
    ConstraintValidator create(TypeName typeName,
                               Annotation constraintAnnotation);
}
