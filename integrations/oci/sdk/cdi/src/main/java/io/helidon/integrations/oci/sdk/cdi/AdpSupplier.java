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
import java.util.function.Supplier;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

/**
 * A {@link Supplier} of {@link Optional} instances {@linkplain Optional#get() housing} {@link
 * BasicAuthenticationDetailsProvider} instances.
 *
 * <p>Note: "{@code Adp}" is a convenient abbreviation for the otherwise cumbersome text "{@code
 * AuthenticationDetailsProvider}".</p>
 *
 * @param <T> a {@link BasicAuthenticationDetailsProvider} subtype
 *
 * @see #get()
 *
 * @see BasicAuthenticationDetailsProvider
 */
@FunctionalInterface
interface AdpSupplier<T extends BasicAuthenticationDetailsProvider> extends Supplier<Optional<T>> {

    // See
    // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-common/src/main/java/com/oracle/bmc/http/internal/BaseClient.java#L103,
    // which leads to
    // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-common/src/main/java/com/oracle/bmc/http/signing/internal/DefaultRequestSignerFactory.java#L42-L45
    //
    // The upshot is that although the common contract for almost all services in OCI is to authenticate using
    // AbstractAuthenticationDetailsProvider instances, in nearly all usage scenarios authentication uses
    // BasicAuthenticationDetailsProvider instances. This AdpSupplier class enforces that.

    /**
     * Returns an {@link Optional} {@linkplain Optional#get() housing} a {@link BasicAuthenticationDetailsProvider}
     * instance.
     *
     * <p>The contract of {@link Supplier#get()} is amended as follows:</p>
     *
     * <p>An implementation of this class <em>may</em> perform <em>availability checks</em> of its choosing before
     * deciding whether to return an {@linkplain Optional#isEmpty() empty <code>Optional</code>} or a {@linkplain
     * Optional#isPresent() present one}. The availability checks <em>should</em> be fast and minimal: the goal of such
     * checks <em>must</em> be to see if only those requirements that are absolutely necessary for the {@link
     * BasicAuthenticationDetailsProvider} to function are met. Additional validation <em>should</em> be carried out by
     * the {@link BasicAuthenticationDetailsProvider} instance in question if or when it is actually used.</p>
     *
     * <p>An {@linkplain Optional#isEmpty() empty <code>Optional</code>} return value indicates only that at the moment
     * of invocation minimal requirements were not met. It implies no further semantics of any kind.</p>
     *
     * <p>There is no obligation for implementations of this method to produce a determinate value.</p>
     *
     * <p>Implementations of this method need not be safe for concurrent use by multiple threads.</p>
     *
     * @return an {@link Optional} {@linkplain Optional#get() housing} a {@link BasicAuthenticationDetailsProvider}
     * instance, never {@code null}
     *
     * @exception RuntimeException if an error occurs retrieving or producing the {@link Optional} that is to be
     * returned
     *
     * @see Supplier#get()
     *
     * @see BasicAuthenticationDetailsProvider
     */
    Optional<T> get();

}
