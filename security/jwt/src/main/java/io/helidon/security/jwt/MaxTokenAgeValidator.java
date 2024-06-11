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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Errors;

/**
 * Max token age validator.
 */
public final class MaxTokenAgeValidator extends InstantValidator {
    private final Duration expectedMaxTokenAge;

    private MaxTokenAgeValidator(Builder builder) {
        super(builder);
        this.expectedMaxTokenAge = builder.expectedMaxTokenAge;
    }

    /**
     * Return a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder()
                .addClaim(Jwt.ISSUED_AT)
                .missingClaimMessage("Claim iat is required to be present in JWT when validating token max allowed age.");
    }

    @Override
    public void validate(Jwt jwt, Errors.Collector collector, List<ClaimValidator> validators) {
        Optional<Instant> maybeIssueTime = jwt.issueTime();
        maybeIssueTime.ifPresent(issueTime -> {
            Instant now = instant();
            Instant earliest = earliest(issueTime);
            Instant latest = latest(earliest).plus(expectedMaxTokenAge);
            if (earliest.isBefore(now) && latest.isAfter(now)) {
                return;
            }
            collector.fatal(jwt, "Current time need to be between " + earliest
                    + " and " + latest + ", but was " + now);
        });
        super.validate(Jwt.ISSUED_AT, maybeIssueTime, collector);
    }

    /**
     * Builder of the {@link MaxTokenAgeValidator}.
     */
    public static final class Builder extends InstantValidator.BaseBuilder<Builder, MaxTokenAgeValidator> {

        private Duration expectedMaxTokenAge = null;

        private Builder() {
        }

        @Override
        public MaxTokenAgeValidator build() {
            Objects.requireNonNull(expectedMaxTokenAge, "Expected JWT max token age is required to be set");
            return new MaxTokenAgeValidator(this);
        }

        /**
         * Expected max token age.
         *
         * @param expectedMaxTokenAge max token age
         * @return updated builder instance
         */
        public Builder expectedMaxTokenAge(Duration expectedMaxTokenAge) {
            this.expectedMaxTokenAge = Objects.requireNonNull(expectedMaxTokenAge);
            return this;
        }
    }

}
