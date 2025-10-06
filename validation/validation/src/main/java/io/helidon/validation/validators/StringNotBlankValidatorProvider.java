package io.helidon.validation.validators;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.String.NotBlank.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringNotBlankValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        return new NotBlankValidator(constraintAnnotation);
    }

    private static class NotBlankValidator extends BaseValidator {
        private NotBlankValidator(Annotation annotation) {
            super(annotation,
                  "is blank",
                  NotBlankValidator::validate);
        }

        private static boolean validate(Object value) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            return !chars.toString().isBlank();
        }
    }
}
