package io.helidon.validation.validators;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.Boolean.True.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class BooleanTrueValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        return new BaseValidator(constraintAnnotation,
                                 "must be true",
                                 it -> it instanceof Boolean && (Boolean) it);
    }
}
