package io.helidon.validation.validators;

import java.util.List;
import java.util.regex.Pattern;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.String.Pattern.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringPatternValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int flags = 0;
        String regexp = constraintAnnotation.value().orElseThrow();
        var annotationFlags = constraintAnnotation.enumValues("flags",
                                                              Constraints.String.Pattern.Flag.class)
                .orElseGet(List::of);

        for (Constraints.String.Pattern.Flag annotationFlag : annotationFlags) {
            flags |= annotationFlag.value();
        }

        Pattern pattern = Pattern.compile(regexp, flags);

        int finalFlags = flags;

        return new PatternValidator(constraintAnnotation,
                                    pattern,
                                    "does not match pattern \"" + regexp + "\" with flags " + finalFlags);
    }

    private static class PatternValidator extends BaseValidator {
        private PatternValidator(Annotation annotation, Pattern pattern, String defaultMessage) {
            super(annotation,
                  defaultMessage,
                  it -> validate(it, pattern));
        }

        private static boolean validate(Object value, Pattern pattern) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            return pattern.matcher(chars).matches();
        }
    }
}
