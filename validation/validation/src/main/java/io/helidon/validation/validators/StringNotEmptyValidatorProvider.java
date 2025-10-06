package io.helidon.validation.validators;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.String.NotEmpty.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringNotEmptyValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        return new NotEmptyValidator(constraintAnnotation);
    }

    private static class NotEmptyValidator extends BaseValidator {
        private NotEmptyValidator(Annotation annotation) {
            super(annotation,
                  "is blank",
                  NotEmptyValidator::validate);
        }

        private static boolean validate(Object value) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            return !chars.isEmpty();
        }
    }
}
