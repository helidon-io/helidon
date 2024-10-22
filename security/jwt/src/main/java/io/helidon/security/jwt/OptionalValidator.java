/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

import java.util.Optional;

import io.helidon.common.Errors;

abstract class OptionalValidator extends CommonValidator {
    private final boolean mandatory;
    private final String missingClaimMessage;

    OptionalValidator(BaseBuilder<?, ?> builder) {
        super(builder);
        this.mandatory = builder.mandatory;
        this.missingClaimMessage = builder.missingClaimMessage;
    }

    <T> Optional<T> validate(String name, Optional<T> optional, Errors.Collector collector) {
        if (mandatory && optional.isEmpty()) {
            String message;
            if (missingClaimMessage == null) {
                message = "Field " + name + " is mandatory, yet not defined in JWT";
            } else {
                message = missingClaimMessage;
            }
            collector.fatal(message);
        }
        return optional;
    }

    abstract static class BaseBuilder<B extends BaseBuilder<B, T>, T> extends CommonValidator.BaseBuilder<B, T> {

        private boolean mandatory = false;
        private String missingClaimMessage;

        BaseBuilder() {
        }

        /**
         * Whether handled claim is mandatory to be present.
         * Default value is {@code false}.
         *
         * @param mandatory mandatory to be present
         * @return updated builder instance
         */
        public B mandatory(boolean mandatory) {
            this.mandatory = mandatory;
            return me();
        }

        /**
         * Custom missing claim error message.
         *
         * @param missingClaimMessage missing claim error message
         * @return updated builder instance
         */
        public B missingClaimMessage(String missingClaimMessage) {
            this.missingClaimMessage = missingClaimMessage;
            return me();
        }
    }
}
