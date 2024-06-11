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
import java.util.Objects;
import java.util.Set;

abstract class CommonValidator implements ClaimValidator {

    private final JwtScope scope;
    private final Set<String> claims;

    CommonValidator(BaseBuilder<?, ?> builder) {
        this.scope = builder.scope;
        this.claims = Set.copyOf(builder.claims);
    }

    @Override
    public JwtScope jwtScope() {
        return scope;
    }

    @Override
    public Set<String> claims() {
        return claims;
    }

    abstract static class BaseBuilder<B extends BaseBuilder<B, T>, T>
            implements io.helidon.common.Builder<BaseBuilder<B, T>, T> {

        private JwtScope scope = JwtScope.PAYLOAD;
        private Set<String> claims = new HashSet<>();

        BaseBuilder() {
        }

        /**
         * The scope of JWT.
         * Default value is {@link JwtScope#PAYLOAD}.
         *
         * @param scope jwt scope
         * @return updated builder instance
         */
        B scope(JwtScope scope) {
            this.scope = Objects.requireNonNull(scope);
            return me();
        }

        /**
         * Add JWT claim this validator is bound to.
         *
         * @param claim claim name
         * @return updated builder instance
         */
        B addClaim(String claim) {
            this.claims.add(claim);
            return me();
        }

        /**
         * Add JWT claim this validator is bound to.
         *
         * @param claims bound claims
         * @return updated builder instance
         */
        B claims(Set<String> claims) {
            this.claims = new HashSet<>(claims);
            return me();
        }

        /**
         * Clear all set claims.
         *
         * @return updated builder instance
         */
        B clearClaims() {
            this.claims.clear();
            return me();
        }

        /**
         * Currently set {@link JwtScope} scope value.
         *
         * @return scope value
         */
        JwtScope scope() {
            return scope;
        }

        @SuppressWarnings("unchecked")
        protected B me() {
            return (B) this;
        }
    }
}
