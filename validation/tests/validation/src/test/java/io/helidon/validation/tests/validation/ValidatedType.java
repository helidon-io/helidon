package io.helidon.validation.tests.validation;

import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Validation.Validated
record ValidatedType(@Constraints.String.Pattern(".*test.*") String first,
                     @Constraints.Integer.Min(42) int second) {

}
