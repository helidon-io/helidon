package io.helidon.validation.validators;

import java.util.Locale;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidationException;

import static io.helidon.validation.validators.IntegerMaxValidatorProvider.charToString;

@Service.NamedByType(Constraints.Integer.Min.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class IntegerMinValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int min = constraintAnnotation.intValue().orElse(Integer.MIN_VALUE);

        // supported types are:
        /*
         int, Integer
         short,  Short
         byte, Byte
         char, Character
         */
        if (type.equals(TypeNames.PRIMITIVE_INT) || type.equals(TypeNames.BOXED_INT)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is less than " + min,
                                     it -> it instanceof Integer value && value >= min);
        }

        if (type.equals(TypeNames.PRIMITIVE_LONG) || type.equals(TypeNames.BOXED_LONG)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is less than " + min,
                                     it -> it instanceof Long value && value >= min);
        }

        if (type.equals(TypeNames.PRIMITIVE_SHORT) || type.equals(TypeNames.BOXED_SHORT)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is less than " + min,
                                     it -> it instanceof Short value && value >= min);
        }

        if (type.equals(TypeNames.PRIMITIVE_BYTE) || type.equals(TypeNames.BOXED_BYTE)) {
            return new BaseValidator(constraintAnnotation,
                                     "0x%1$02x is less than 0x" + Integer.toHexString(min).toUpperCase(Locale.ROOT),
                                     it -> it instanceof Byte value && (value & 0xFF) >= min);
        }

        if (type.equals(TypeNames.PRIMITIVE_CHAR) || type.equals(TypeNames.BOXED_CHAR)) {
            return new CharValidator(constraintAnnotation, min);
        }

        throw new ValidationException("Invalid type of Integer.Min: " + type.fqName());
    }

    private static class CharValidator extends BaseValidator {

        private CharValidator(Annotation constraintAnnotation, int min) {
            super(constraintAnnotation,
                  "'%1$c' (%1$d) is less than " + charToString(min),
                  it -> it instanceof Character value && value >= min);
        }

        @Override
        protected Object convertValue(Object object) {
            if (object instanceof Character c) {
                return (int) c;
            }
            return object;
        }
    }
}
