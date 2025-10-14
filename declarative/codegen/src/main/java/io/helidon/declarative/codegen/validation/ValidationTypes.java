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

package io.helidon.declarative.codegen.validation;

import io.helidon.common.types.TypeName;

final class ValidationTypes {

    static final TypeName VALIDATION_CONTEXT = TypeName.create("io.helidon.validation.ValidationContext");
    static final TypeName VALIDATION_CONSTRAINT = TypeName.create("io.helidon.validation.Validation.Constraint");
    static final TypeName VALIDATION_VALIDATED = TypeName.create("io.helidon.validation.Validation.Validated");
    static final TypeName VALIDATION_VALID = TypeName.create("io.helidon.validation.Validation.Valid");

    static final TypeName CONSTRAINT_VIOLATION_LOCATION = TypeName.create("io.helidon.validation.ConstraintViolation.Location");

    static final TypeName VALIDATION_EXCEPTION = TypeName.create("io.helidon.validation.ValidationException");
    static final TypeName VALIDATOR_RESPONSE = TypeName.create("io.helidon.validation.ValidatorResponse");

    static final TypeName TYPE_VALIDATOR = TypeName.create("io.helidon.validation.spi.TypeValidator");
    static final TypeName CONSTRAINT_VALIDATOR = TypeName.create("io.helidon.validation.spi.ConstraintValidator");
    static final TypeName CONSTRAINT_VALIDATOR_PROVIDER =
            TypeName.create("io.helidon.validation.spi.ConstraintValidatorProvider");

    private ValidationTypes() {
    }
}
