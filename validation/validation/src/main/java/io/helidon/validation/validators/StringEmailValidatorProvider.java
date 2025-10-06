package io.helidon.validation.validators;

import java.util.regex.Pattern;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.NamedByType(Constraints.String.Email.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringEmailValidatorProvider implements Validation.ConstraintValidatorProvider {
    @Override
    public Validation.ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        return new EmailValidator(constraintAnnotation);
    }

    private static class EmailValidator extends BaseValidator {
        // user may be sections separated by dots excluding special characters
        private static final String SPECIAL_CHARS = "\\p{Cntrl}\\(\\)<>@,;:'\\\\\\\"\\.\\[\\]";
        private static final String VALID_CHARS = "(\\\\.)|[^\\s" + SPECIAL_CHARS + "]";
        private static final String QUOTED_USER = "(\"(\\\\\"|[^\"])*\")";
        private static final String SECTION = "((" + VALID_CHARS + "|')+|" + QUOTED_USER + ")";
        private static final String USER = "^" + SECTION + "(\\." + SECTION + ")*$";
        private static final Pattern USER_PATTERN = Pattern.compile(USER);
        // see section 4.5.3.1.1.  Local-part of https://www.rfc-editor.org/rfc/rfc5321.html
        private static final int MAX_USER_LENGTH = 64;
        // see section 4.5.3.1.2.  Domain of the same RFC
        private static final int MAX_DOMAIN_LENGTH = 255;

        private EmailValidator(Annotation annotation) {
            super(annotation,
                  "does not match e-mail pattern",
                  EmailValidator::validate);
        }

        private static boolean validate(Object value) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            return validate(chars.toString());
        }

        private static boolean validate(String value) {
            int atSign = value.lastIndexOf('@');

            if (atSign <= 0) {
                return false;
            }

            String user = value.substring(0, atSign);

            if (user.length() > MAX_USER_LENGTH) {
                return false;
            }

            var userMatcher = USER_PATTERN.matcher(user);

            if (!userMatcher.matches()) {
                return false;
            }

            String domain = value.substring(atSign + 1);
            // we have a limited validation of the domain part
            // 1. must not end with a dot
            if (domain.charAt(domain.length() - 1) == '.') {
                return false;
            }

            if (domain.length() > MAX_DOMAIN_LENGTH) {
                return false;
            }

            // there are other checks that could be done
            // we can improve this later
            return true;
        }
    }
}
