/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Services;
import io.helidon.validation.TypeValidation;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidationContext;
import io.helidon.validation.Validators;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@SuppressWarnings("ALL")
class ValidationSnippets {

    static void snippet_2() {
        // tag::snippet_2[]
        TypeValidation validator = Services.get(TypeValidation.class);
        var validationResponse = validator.validate(MyType.class, new MyType("valid", 43));
        // end::snippet_2[]
    }

    static void snippet_3() {
        // tag::snippet_3[]
        TypeValidation validator = Services.get(TypeValidation.class);
        // throws a ValidationException if the object is invalid
        validator.check(MyType.class, new MyType("valid", 43));
        // end::snippet_3[]
    }

    static void snippet_4() {
        Object anInstance = null;

        // tag::snippet_4[]
        var validationResponse = Validators.validateNotNull(anInstance);
        // end::snippet_4[]
    }

    static void snippet_5() {
        Object anInstance = null;
        // tag::snippet_5[]
        // throws a ValidationException if the object is invalid
        Validators.checkNotNull(anInstance);
        // end::snippet_5[]
    }

    static void snippet_6() {
        String anInstance = "valid";
        // tag::snippet_6[]
        var provider = Services.getNamed(ConstraintValidatorProvider.class, Validation.String.Pattern.class.getName()); // <1>
        var context = ValidationContext.create(MyType.class); // <2>
        var validator = provider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class, ".*valid.*")); // <3>
        context.check(validator, anInstance); // <4>
        var response = context.response(); // <5>
        // end::snippet_6[]
    }

    static void snippet_7() {
        String anInstance = "valid";
        // tag::snippet_7[]
        var provider = Services.getNamed(ConstraintValidatorProvider.class, Validation.String.Pattern.class.getName()); // <1>
        var context = ValidationContext.create(MyType.class); // <2>
        var validator = provider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class, ".*valid.*")); // <3>
        context.check(validator, anInstance); // <4>
        context.throwOnFailure(); // <5>
        // end::snippet_7[]
    }

    // tag::snippet_1[]
    @Validation.Validated
    record MyType(@Validation.String.Pattern(".*valid.*") @Validation.NotNull String validString,
                  @Validation.Integer.Min(42) int validInt) {
    }
    // end::snippet_1[]
}
