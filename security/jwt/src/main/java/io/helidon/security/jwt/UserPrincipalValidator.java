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

import io.helidon.common.Errors;

/**
 * User principal validator.
 */
public final class UserPrincipalValidator extends OptionalValidator {

    private UserPrincipalValidator(Builder builder) {
        super(builder);
    }

    /**
     * Return a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder()
                .addClaim(Jwt.USER_PRINCIPAL)
                .mandatory(true);
    }

    @Override
    public void validate(Jwt object, Errors.Collector collector, List<ClaimValidator> validators) {
        super.validate("User Principal", object.userPrincipal(), collector);
    }

    /**
     * Builder of the {@link UserPrincipalValidator}.
     */
    public static final class Builder extends OptionalValidator.BaseBuilder<Builder, UserPrincipalValidator> {

        private Builder() {
        }

        @Override
        public UserPrincipalValidator build() {
            return new UserPrincipalValidator(this);
        }
    }
}
