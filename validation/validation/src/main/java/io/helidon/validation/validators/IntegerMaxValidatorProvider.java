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

@Service.NamedByType(Constraints.Integer.Max.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class IntegerMaxValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int max = constraintAnnotation.intValue().orElse(Integer.MAX_VALUE);

        // supported types are:
        /*
         int, Integer
         short,  Short
         byte, Byte
         char, Character
         */
        if (type.equals(TypeNames.PRIMITIVE_INT) || type.equals(TypeNames.BOXED_INT)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is more than " + max,
                                    it -> it instanceof Integer value && value <= max);
        }

        if (type.equals(TypeNames.PRIMITIVE_LONG) || type.equals(TypeNames.BOXED_LONG)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is more than " + max,
                                    it -> it instanceof Long value && value <= max);
        }

        if (type.equals(TypeNames.PRIMITIVE_SHORT) || type.equals(TypeNames.BOXED_SHORT)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is more than " + max,
                                    it -> it instanceof Short value && value <= max);
        }

        if (type.equals(TypeNames.PRIMITIVE_BYTE) || type.equals(TypeNames.BOXED_BYTE)) {
            return new BaseValidator(constraintAnnotation,
                                     "0x%1$02x is more than 0x" + Integer.toHexString(max).toUpperCase(Locale.ROOT),
                                    it -> it instanceof Byte value && (value & 0xFF) <= max);
        }

        if (type.equals(TypeNames.PRIMITIVE_CHAR) || type.equals(TypeNames.BOXED_CHAR)) {
            return new CharValidator(constraintAnnotation, max);
        }

        throw new ValidationException("Invalid type of Integer.Max: " + type.fqName());
    }

    static String charToString(int max) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(max);

        if (!Character.isISOControl(max)
                && block != null
                && block != Character.UnicodeBlock.SPECIALS) {
            return "'" + (char) max + "' (" + max + ")";
        } else {
            return String.valueOf(max);
        }
    }

    private static class CharValidator extends BaseValidator {

        private CharValidator(Annotation constraintAnnotation, int max) {
            super(constraintAnnotation,
                  "'%1$c' (%1$d) is more than " + charToString(max),
                  it -> it instanceof Character value && value <= max);
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
