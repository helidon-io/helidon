package io.helidon.validation.validators;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.Number.Max.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class NumberMaxValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        String max = constraintAnnotation.value().orElseThrow();
        BigDecimal minNumber = new BigDecimal(max);

        if (type.equals(TypeNames.STRING) || type.equals(TypeName.create(CharSequence.class))) {
            return new ValidatorFromString(constraintAnnotation, minNumber);
        }
        return new ValidatorFromNumber(constraintAnnotation, minNumber);
    }

    private static final class ValidatorFromString extends BaseValidator {

        private ValidatorFromString(Annotation annotation, BigDecimal maxNumber) {
            super(annotation,
                  "%s is bigger than " + maxNumber,
                  it -> validate(it, maxNumber));
        }

        private static boolean validate(Object value, BigDecimal maxNumber) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }

            try {
                BigDecimal bd = new BigDecimal(chars.toString());
                return bd.compareTo(maxNumber) <= 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static final class ValidatorFromNumber extends BaseValidator {

        private ValidatorFromNumber(Annotation annotation, BigDecimal maxNumber) {
            super(annotation,
                  "%s is bigger than " + maxNumber,
                  it -> validate(it, maxNumber));
        }

        private static boolean validate(Object value, BigDecimal maxNumber) {
            if (!(value instanceof Number number)) {
                return false;
            }

            BigDecimal asBd;
            if (number instanceof BigDecimal bd) {
                asBd = bd;
            } else if (number instanceof BigInteger bi) {
                asBd = new BigDecimal(bi);
            } else {
                asBd = new BigDecimal(number.doubleValue());
            }

            return asBd.compareTo(maxNumber) <= 0;
        }
    }
}
