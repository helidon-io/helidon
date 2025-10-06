package io.helidon.validation.tests.validation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Validation.Validated
class NestedValidated {
    @Constraints.NotNull
    final String value;
    final Optional<@Validation.Valid ValidatedType> validatedType;
    final Set<@Constraints.String.NotBlank @Constraints.String.Length(4) String> validatedSet = new HashSet<>();
    final Map<@Constraints.String.NotBlank @Constraints.String.Length(4) String,
            @Constraints.String.NotBlank @Constraints.String.Length(7) String> validatedMap = new HashMap<>();
    final String notValidated = "Hello World!";

    private final long bigNumber;

    NestedValidated(String value, long bigNumber, ValidatedType validatedType) {
        this.value = value;
        this.bigNumber = bigNumber;
        this.validatedType = Optional.ofNullable(validatedType);
    }

    @Constraints.Long.Max(14569L)
    public long bigNumber() {
        return bigNumber;
    }
}
