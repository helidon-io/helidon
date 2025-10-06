package io.helidon.validation.validators;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.String.Length.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringLengthValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int minLength = constraintAnnotation.intValue("min").orElse(0);
        int maxLength = constraintAnnotation.intValue("value").orElse(Integer.MAX_VALUE);
        if (minLength == 0) {
            return new StringLengthValidator(constraintAnnotation, maxLength, true);
        }
        if (maxLength == Integer.MAX_VALUE) {
            return new StringLengthValidator(constraintAnnotation, minLength);
        }
        return new StringLengthValidator(constraintAnnotation, minLength, maxLength);
    }

    private static final class StringLengthValidator extends BaseValidator {
        private StringLengthValidator(Annotation annotation, int minLength, int maxLength) {
            super(annotation,
                  "is shorter than " + minLength + " or longer than " + maxLength + " characters",
                  it -> validate(it, minLength, maxLength));
        }

        private StringLengthValidator(Annotation annotation, int minLength) {
            super(annotation,
                  "is shorter than " + minLength + " characters",
                  it -> validate(it, minLength, Integer.MAX_VALUE));
        }

        private StringLengthValidator(Annotation annotation, int maxLength, boolean ignored) {
            super(annotation,
                  "is longer than " + maxLength + " characters",
                  it -> validate(it, 0, maxLength));
        }

        private static boolean validate(Object value, int minLength, int maxLength) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            int len = chars.length();

            return len >= minLength && len <= maxLength;
        }
    }
}
