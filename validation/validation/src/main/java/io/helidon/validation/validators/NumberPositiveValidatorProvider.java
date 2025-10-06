package io.helidon.validation.validators;

import java.math.BigDecimal;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.Number.Positive.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class NumberPositiveValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        if (type.equals(TypeNames.STRING) || type.equals(TypeName.create(CharSequence.class))) {
            return new ValidatorFromString(constraintAnnotation);
        }
        return new ValidatorFromNumber(constraintAnnotation);
    }

    private static final class ValidatorFromString extends BaseValidator {

        private ValidatorFromString(Annotation annotation) {
            super(annotation,
                  "%s is not a positive number",
                  ValidatorFromString::validate);

        }

        private static boolean validate(Object value) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            BigDecimal bd;
            try {
                bd = new BigDecimal(chars.toString());
            } catch (Exception e) {
                return false;
            }

            return bd.signum() > 0;
        }
    }

    private static final class ValidatorFromNumber extends BaseValidator {

        private ValidatorFromNumber(Annotation annotation) {
            super(annotation,
                  "%d is not positive",
                  ValidatorFromNumber::validate);

        }

        private static boolean validate(Object value) {
            if (!(value instanceof Number number)) {
                return false;
            }
            return number.doubleValue() > 0D;
        }
    }
}
