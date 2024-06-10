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
package io.helidon.integrations.oci.sdk.cdi;

import java.util.Optional;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

/**
 * An {@link AdpSupplier} that always {@linkplain #get() returns} an {@linkplain Optional#isEmpty() empty
 * <code>Optional</code>} from its {@link #get()} method.
 */
final class EmptyAdpSupplier implements AdpSupplier<BasicAuthenticationDetailsProvider> {


    /*
     * Static fields.
     */


    /**
     * The sole instance of this class.
     */
    private static final EmptyAdpSupplier INSTANCE = new EmptyAdpSupplier();


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link EmptyAdpSupplier}.
     */
    private EmptyAdpSupplier() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Returns an {@linkplain Optional#isEmpty() empty <code>Optional</code>} when invoked.
     *
     * @return an {@linkplain Optional#isEmpty() empty <code>Optional</code>} when invoked; never {@code null}
     */
    @Override
    public Optional<BasicAuthenticationDetailsProvider> get() {
        return Optional.empty();
    }


    /*
     * Static methods.
     */


    /**
     * Returns an {@link EmptyAdpSupplier}.
     *
     * @return an {@link EmptyAdpSupplier}; never {@code null}
     */
    public static EmptyAdpSupplier of() {
        return INSTANCE;
    }

}
