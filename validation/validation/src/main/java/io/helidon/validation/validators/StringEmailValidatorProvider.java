/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.validation.validators;

import java.util.regex.Pattern;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Service.NamedByType(Validation.String.Email.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringEmailValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        return new EmailValidator(constraintAnnotation);
    }

    private static class EmailValidator extends BaseValidator {
        // user may be sections separated by dots excluding special characters
        private static final String LOCAL_PART_CORE = "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]";
        private static final String LOCAL_PART_INSIDE_QUOTES =
                "([a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")";
        private static final String USER =
                "(" + LOCAL_PART_CORE + "+|\"" + LOCAL_PART_INSIDE_QUOTES + "+\")"
                + "(\\." + "(" + LOCAL_PART_CORE + "+|\"" + LOCAL_PART_INSIDE_QUOTES + "+\")" + ")*";

        private static final Pattern USER_PATTERN = Pattern.compile(USER, CASE_INSENSITIVE);

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

            if (domain.isBlank()) {
                return false;
            }

            // we have a limited validation of the domain part
            // must not end with a dot
            if (domain.charAt(domain.length() - 1) == '.') {
                return false;
            }

            // must not start with a dot
            if (domain.charAt(0) == '.') {
                return false;
            }

            // max length
            if (domain.length() > MAX_DOMAIN_LENGTH) {
                return false;
            }

            // top level domain not supported
            if (domain.indexOf('.') < 0) {
                return false;
            }

            // underscores not supported
            if (domain.indexOf('_') > -1) {
                return false;
            }

            // spaces not supported
            if (domain.indexOf(' ') > -1) {
                return false;
            }

            // there are other checks that could be done
            // we can improve this later
            return true;
        }
    }
}
