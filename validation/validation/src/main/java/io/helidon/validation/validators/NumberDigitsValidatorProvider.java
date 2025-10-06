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

@Service.NamedByType(Constraints.Number.Digits.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class NumberDigitsValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int scale = constraintAnnotation.intValue("scale").orElse(-1);
        int precision = constraintAnnotation.intValue("precision").orElse(-1);

        if (type.equals(TypeNames.STRING) || type.equals(TypeName.create(CharSequence.class))) {
            return new ValidatorFromString(constraintAnnotation, scale, precision);
        }
        // this should be improved eventually to support various types of numbers directly, to avoid
        // guesswork at the time of validation
        return new ValidatorFromNumber(constraintAnnotation, scale, precision);
    }

    private static String defaultMessage(int scale, int precision) {
        if (scale == -1 && precision == -1) {
            return "";
        }
        if (scale == -1) {
            return "%d has invalid precision, it should be " + precision;
        }
        if (precision == -1) {
            return "%d has invalid scale, it should be " + scale;
        }
        return "%d has invalid scale or precision, they should be " + scale + " (scale) and " + precision + " (precision)";
    }

    private static boolean validate(BigDecimal value, int scale, int precision) {
        if (precision > -1) {
            if (value.precision() > precision) {
                return false;
            }
        }
        if (scale > -1) {
            if (value.scale() > scale) {
                return false;
            }
        }
        return true;
    }

    private static final class ValidatorFromString extends BaseValidator
            implements Validation.ConstraintValidator {

        private ValidatorFromString(Annotation annotation, int scale, int precision) {
            super(annotation,
                  defaultMessage(scale, precision),
                  it -> validate(it, scale, precision));
        }

        private static boolean validate(Object value, int scale, int precision) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }

            BigDecimal bd;
            try {
                bd = new BigDecimal(chars.toString());
            } catch (Exception e) {
                return false;
            }

            return NumberDigitsValidatorProvider.validate(bd, scale, precision);
        }
    }

    private static final class ValidatorFromNumber extends BaseValidator
            implements Validation.ConstraintValidator {

        private ValidatorFromNumber(Annotation annotation, int scale, int precision) {
            super(annotation,
                  defaultMessage(scale, precision),
                  it -> validate(it, scale, precision));
        }

        private static boolean validate(Object value, int scale, int precision) {
            if (!(value instanceof Number number)) {
                return false;
            }

            if (number instanceof BigDecimal bd) {
                return NumberDigitsValidatorProvider.validate(bd, scale, precision);
            } else if (number instanceof BigInteger bi) {
                return NumberDigitsValidatorProvider.validate(new BigDecimal(bi), scale, precision);
            } else {
                return NumberDigitsValidatorProvider.validate(new BigDecimal(number.doubleValue()), scale, precision);
            }
        }
    }
}
