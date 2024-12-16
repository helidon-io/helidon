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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Errors;

/**
 * Audience claim validator.
 */
public final class AudienceValidator extends OptionalValidator {
    private final Set<String> expectedAudience;

    private AudienceValidator(Builder builder) {
        super(builder);
        this.expectedAudience = Set.copyOf(builder.expectedAudience);
    }

    /**
     * Return a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder()
                .addClaim(Jwt.AUDIENCE)
                .mandatory(true);
    }

    @Override
    public void validate(Jwt jwt, Errors.Collector collector, List<ClaimValidator> validators) {
        Optional<List<String>> jwtAudiences = jwt.audience();
        jwtAudiences.ifPresent(jwtAudience -> {
            if (expectedAudience.stream().anyMatch(jwtAudiences.get()::contains)) {
                return;
            }
            collector.fatal(jwt, "Audience must contain " + expectedAudience + ", yet it is: " + jwtAudiences);
        });
        super.validate(Jwt.AUDIENCE, jwtAudiences, collector);
    }

    /**
     * Builder of the {@link AudienceValidator}.
     */
    public static final class Builder extends OptionalValidator.BaseBuilder<Builder, AudienceValidator> {

        private Set<String> expectedAudience = new HashSet<>();

        private Builder() {
        }

        @Override
        public AudienceValidator build() {
            return new AudienceValidator(this);
        }

        /**
         * Add expected audience value.
         *
         * @param audience expected audience
         * @return updated builder instance
         */
        public Builder addExpectedAudience(String audience) {
            Objects.requireNonNull(audience);
            expectedAudience.add(audience);
            return this;
        }

        /**
         * Overwrite previously set audience with the new {@link Set} of values.
         *
         * @param expectedAudience expected audience values
         * @return updated builder instance
         */
        public Builder expectedAudience(Set<String> expectedAudience) {
            Objects.requireNonNull(expectedAudience);
            this.expectedAudience = new HashSet<>(expectedAudience);
            return this;
        }
    }
}
