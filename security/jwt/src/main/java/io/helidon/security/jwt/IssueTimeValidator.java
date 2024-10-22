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
 * Validator of the issue time claim.
 */
public final class IssueTimeValidator extends InstantValidator {

    private IssueTimeValidator(Builder builder) {
        super(builder);
    }

    /**
     * Return a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder().addClaim(Jwt.ISSUED_AT);
    }

    @Override
    public void validate(Jwt token, Errors.Collector collector, List<ClaimValidator> validators) {
        Optional<Instant> issueTime = token.issueTime();
        issueTime.ifPresent(it -> {
            // must be issued in the past
            if (latest().isBefore(it)) {
                collector.fatal(token, "Token was not issued in the past: " + it);
            }
        });
        // ensure we fail if mandatory and not present
        super.validate("issueTime", issueTime, collector);
    }

    /**
     * Builder of the {@link IssueTimeValidator}.
     */
    public static final class Builder extends InstantValidator.BaseBuilder<Builder, IssueTimeValidator> {

        private Builder() {
        }

        @Override
        public IssueTimeValidator build() {
            return new IssueTimeValidator(this);
        }
    }
}
