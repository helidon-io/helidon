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
 * Validator of not before claim.
 */
public final class NotBeforeValidator extends InstantValidator {

    private NotBeforeValidator(NotBeforeValidator.Builder builder) {
        super(builder);
    }

    /**
     * Return a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder().addClaim(Jwt.NOT_BEFORE);
    }

    @Override
    public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
        Optional<Instant> notBefore = token.notBefore();
        notBefore.ifPresent(it -> {
            if (latest().isBefore(it)) {
                collector.fatal(token, "Token not yet valid, not before: " + it);
            }
        });
        // ensure we fail if mandatory and not present
        super.validate("notBefore", notBefore, collector);
    }

    /**
     * Builder of the {@link NotBeforeValidator}.
     */
    public static final class Builder extends InstantValidator.BaseBuilder<Builder, NotBeforeValidator> {

        private Builder() {
        }

        @Override
        public NotBeforeValidator build() {
            return new NotBeforeValidator(this);
        }
    }
}
