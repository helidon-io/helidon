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

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import io.helidon.integrations.common.rest.ApiRestException;

/**
 * Vault runtime exception.
 */
public class VaultRestException extends ApiRestException {
    private final List<String> vaultErrors;

    /**
     * A vault exception.
     */
    private VaultRestException(Builder builder) {
        super(builder);
        this.vaultErrors = List.copyOf(builder.vaultErrors);
    }

    /**
     * A builder for Vault exception.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Errors from Vault REST API.
     *
     * @return errors, or empty list
     */
    public List<String> vaultErrors() {
        return vaultErrors;
    }

    @Override
    public void printStackTrace(PrintStream s) {
        s.println("\tVault messages: " + vaultErrors);
        super.printStackTrace(s);
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.vault.VaultRestException}
     * used by {@link io.helidon.integrations.common.rest.RestApiBase}.
     */
    public static class Builder extends BaseBuilder<Builder> implements io.helidon.common.Builder<VaultRestException> {
        private final List<String> vaultErrors = new LinkedList<>();

        private Builder() {
        }

        @Override
        public VaultRestException build() {
            return new VaultRestException(this);
        }

        /**
         * Configure the vault errors read from response entity.
         *
         * @param errors errors to add
         * @return updated builder
         */
        public Builder vaultErrors(List<String> errors) {
            this.vaultErrors.addAll(errors);
            return this;
        }
    }
}
