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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.helidon.common.Errors;

/**
 * Validator of expiration claim.
 */
public final class ExpirationValidator extends InstantValidator {

    private ExpirationValidator(Builder builder) {
        super(builder);
    }

    /**
     * Return a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder()
                .addClaim(Jwt.EXPIRATION)
                .addClaim(Jwt.ISSUED_AT);
    }

    @Override
    public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
        Optional<Instant> expirationTime = token.expirationTime();
        expirationTime.ifPresent(it -> {
            if (earliest().isAfter(it)) {
                collector.fatal(token, "Token no longer valid, expiration: " + it);
            }
            token.issueTime().ifPresent(issued -> {
                if (issued.isAfter(it)) {
                    collector.fatal(token, "Token issue date is after its expiration, "
                            + "issue: " + it + ", expiration: " + it);
                }
            });
        });
        // ensure we fail if mandatory and not present
        super.validate("expirationTime", expirationTime, collector);
    }

    /**
     * Builder of the {@link ExpirationValidator}.
     */
    public static final class Builder extends InstantValidator.BaseBuilder<Builder, ExpirationValidator> {

        private Builder() {
        }

        @Override
        public ExpirationValidator build() {
            return new ExpirationValidator(this);
        }
    }
}
