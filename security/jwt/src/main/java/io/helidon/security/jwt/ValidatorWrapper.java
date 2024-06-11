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
import java.util.Set;

import io.helidon.common.Errors;

/**
 * Wrapper support for {@link Validator} instances.
 */
public final class ValidatorWrapper extends CommonValidator {

    private final Validator<Jwt> validator;

    private ValidatorWrapper(Builder builder) {
        super(builder);
        validator = builder.validator;
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
    public void validate(Jwt jwt, Errors.Collector collector, List<ClaimValidator> validators) {
        validator.validate(jwt, collector);
    }

    /**
     * Builder of the {@link io.helidon.security.jwt.ValidatorWrapper}.
     */
    public static final class Builder extends BaseBuilder<Builder, ValidatorWrapper> {

        private Validator<Jwt> validator;

        private Builder() {
        }

        @Override
        public Builder claims(Set<String> claims) {
            return super.claims(claims);
        }

        @Override
        public Builder addClaim(String claim) {
            return super.addClaim(claim);
        }

        @Override
        public Builder clearClaims() {
            return super.clearClaims();
        }

        @Override
        public Builder scope(JwtScope scope) {
            return super.scope(scope);
        }

        /**
         * Instance of the {@link Validator}.
         *
         * @param validator validator instance
         * @return updated builder instance
         */
        public Builder validator(Validator<Jwt> validator) {
            this.validator = validator;
            return this;
        }

        @Override
        public io.helidon.security.jwt.ValidatorWrapper build() {
            if (validator == null) {
                throw new RuntimeException("No required validator instance was set");
            }
            return new io.helidon.security.jwt.ValidatorWrapper(this);
        }
    }
}
