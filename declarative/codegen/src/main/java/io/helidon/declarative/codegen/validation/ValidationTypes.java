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
    static final TypeName CONSTRAINT = TypeName.create("io.helidon.validation.Validation.Constraint");
    static final TypeName CONSTRAINT_NOT_NULL = TypeName.create("io.helidon.validation.Constraints.NotNull");

    static final TypeName CONSTRAINT_STRING_NOT_BLANK = TypeName.create("io.helidon.validation.Constraints.String.NotBlank");
    static final TypeName CONSTRAINT_STRING_PATTERN = TypeName.create("io.helidon.validation.Constraints.String.Pattern");
    static final TypeName CONSTRAINT_VALIDATION_CONTEXT = TypeName.create("io.helidon.validation.ConstraintValidatorContext");
    static final TypeName CONSTRAINT_VIOLATION_LOCATION = TypeName.create("io.helidon.validation.ConstraintViolation.Location");

    static final TypeName INTEGER_MIN = TypeName.create("io.helidon.validation.Constraints.Integer.Min");

    static final TypeName VALIDATION_VALID = TypeName.create("io.helidon.validation.Validation.Valid");
    static final TypeName VALIDATION_VALIDATED = TypeName.create("io.helidon.validation.Validation.Validated");
    static final TypeName VALIDATION_TYPE_VALIDATOR = TypeName.create("io.helidon.validation.Validation.TypeValidator");
    static final TypeName VALIDATION_CONSTRAINT_VALIDATOR = TypeName.create("io.helidon.validation.Validation"
                                                                                    + ".ConstraintValidator");
    static final TypeName VALIDATION_CONSTANT_VALIDATOR_PROVIDER = TypeName.create(
            "io.helidon.validation.Validation.ConstraintValidatorProvider");
    static final TypeName VALIDATOR_RESPONSE = TypeName.create("io.helidon.validation.Validation.ValidatorResponse");

    private ValidationTypes() {
    }
}
