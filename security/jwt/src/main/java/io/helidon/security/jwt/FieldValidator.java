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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.Errors;

import jakarta.json.JsonString;

/**
 * Validator of a string field obtained from the JWT.
 */
public final class FieldValidator extends OptionalValidator {
    private final Function<Jwt, Optional<String>> fieldAccessor;
    private final String name;
    private final String expectedValue;

    private FieldValidator(Builder builder) {
        super(builder);
        this.name = builder.name;
        this.fieldAccessor = builder.fieldAccessor;
        this.expectedValue = builder.expectedValue;
    }

    /**
     * Return a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
        super.validate(name, fieldAccessor.apply(token), collector)
                .ifPresent(it -> {
                    if (!expectedValue.equals(it)) {
                        collector.fatal(token,
                                        "Expected value of field \"" + name + "\" was \"" + expectedValue + "\", but "
                                                + "actual value is: \"" + it + "\"");
                    }
                });
    }

    /**
     * Builder of the {@link FieldValidator}.
     */
    public static final class Builder extends OptionalValidator.BaseBuilder<Builder, FieldValidator> {

        private String claimKey;
        private String name;
        private String expectedValue;
        private Function<Jwt, Optional<String>> fieldAccessor;

        private Builder() {
        }

        /**
         * Set handled claim key.
         *
         * @param claimKey supported claim key
         * @return updated builder instance
         */
        public Builder claimKey(String claimKey) {
            //This supports only one claim name
            clearClaims();
            addClaim(claimKey);
            this.claimKey = claimKey;
            return this;
        }

        /**
         * Field name value.
         *
         * @param name name of the field
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Expected value to be present in the supported claim.
         *
         * @param expectedValue expected claim value
         * @return updated builder instance
         */
        public Builder expectedValue(String expectedValue) {
            this.expectedValue = expectedValue;
            return this;
        }

        /**
         * Function to extract field from JWT.
         *
         * @param fieldAccessor function to extract field from JWT
         * @return updated builder instance
         */
        public Builder fieldAccessor(Function<Jwt, Optional<String>> fieldAccessor) {
            this.fieldAccessor = fieldAccessor;
            return this;
        }

        @Override
        public Builder scope(JwtScope scope) {
            return super.scope(scope);
        }

        @Override
        public FieldValidator build() {
            if (name == null) {
                throw new RuntimeException("Missing supported field name");
            } else if (expectedValue == null) {
                throw new RuntimeException("Missing expected claim value");
            }
            if (fieldAccessor == null) {
                if (claimKey == null) {
                    throw new RuntimeException("Field accessor or claim key name has to be set.");
                }
                if (scope() == JwtScope.PAYLOAD) {
                    fieldAccessor = jwt -> jwt.payloadClaim(claimKey).map(it -> ((JsonString) it).getString());
                } else {
                    fieldAccessor = jwt -> jwt.headerClaim(claimKey).map(it -> ((JsonString) it).getString());
                }
            }
            return new FieldValidator(this);
        }
    }
}
