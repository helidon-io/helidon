/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault;

import java.time.Duration;
import java.time.Instant;

/**
 * Abstract implementation of token that can be used to access the Vault.
 */
public abstract class VaultTokenBase {
    private final Instant created;
    private final String token;
    private final Duration leaseDuration;
    private final boolean renewable;

    /**
     * Create a new instance using the provided builder.
     * @param builder builder
     */
    protected VaultTokenBase(Builder<?, ?> builder) {
        this.created = builder.created;
        this.token = builder.token;
        this.leaseDuration = builder.leaseDuration;
        this.renewable = builder.renewable;
    }

    /**
     * When this token was created.
     * @return time this token instance was created
     */
    public Instant created() {
        return created;
    }

    /**
     * The token string.
     *
     * @return  token
     */
    public String token() {
        return token;
    }

    /**
     * Lease duration.
     *
     * @return lease duration
     */
    public Duration leaseDuration() {
        return leaseDuration;
    }

    /**
     * Whether the token is renewable.
     *
     * @return if renewable
     */
    public boolean renewable() {
        return renewable;
    }

    /**
     * Base builder class for tokens.
     *
     * @param <B> type of builder
     * @param <T> type of token
     */
    public abstract static class Builder<B extends Builder<B, T>, T> implements io.helidon.common.Builder<T> {
        private Instant created = Instant.now();
        private String token;
        private Duration leaseDuration;
        private boolean renewable;

        protected Builder() {
        }

        /**
         * When the token was created.
         *
         * @param created instant the token was created
         * @return updated builder
         */
        public B created(Instant created) {
            this.created = created;
            return me();
        }

        /**
         * The token to use (actual string representing the token).
         *
         * @param token token string
         * @return updated builder
         */
        public B token(String token) {
            this.token = token;
            return me();
        }

        /**
         * Lease duration.
         *
         * @param leaseDuration lease duration, such as {@link Duration#ofHours(long)}.
         * @return updated builder
         */
        public B leaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
            return me();
        }

        /**
         * Whether the token is renewable or not.
         *
         * @param renewable {@code true} for a renewable token
         * @return updated builder
         */
        public B renewable(boolean renewable) {
            this.renewable = renewable;
            return me();
        }

        @SuppressWarnings("unchecked")
        protected B me() {
            return (B) this;
        }

        protected Instant created() {
            return created;
        }

        protected String token() {
            return token;
        }

        protected Duration leaseDuration() {
            return leaseDuration;
        }

        protected boolean renewable() {
            return renewable;
        }
    }
}
