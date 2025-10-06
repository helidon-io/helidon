package io.helidon.validation.validators;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.Long.Max.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class LongMaxValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        long max = constraintAnnotation.longValue().orElse(Long.MAX_VALUE);

        return new BaseValidator(constraintAnnotation,
                                 "%s is more than " + max,
                                 it -> it instanceof Long value && value <= max);
    }
}
