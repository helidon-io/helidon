package io.helidon.validation.validators;

import java.util.Objects;
import java.util.function.Predicate;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.NotNull.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class NotNullValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        // we do not care about type, we just check if null
        return new BaseValidator(constraintAnnotation,
                                 "is null",
                                 Predicate.not(Objects::isNull),
                                 true);
    }
}
