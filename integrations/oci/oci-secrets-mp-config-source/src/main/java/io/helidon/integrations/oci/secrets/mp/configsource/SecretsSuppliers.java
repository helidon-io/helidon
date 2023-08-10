/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.secrets.mp.configsource;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.SecretsClient;

import static io.helidon.integrations.oci.secrets.mp.configsource.AdpSuppliers.adpSupplier;

final class SecretsSuppliers {

    private SecretsSuppliers() {
        super();
    }

    static Supplier<? extends Secrets> secrets(Function<? super String, ? extends Optional<String>> c) {
        return secrets(adpSupplier(c));
    }

    static Supplier<? extends Secrets> secrets(BasicAuthenticationDetailsProvider adp) {
        Objects.requireNonNull(adp, "adp");
        return secrets(() -> adp);
    }

    static Supplier<? extends Secrets> secrets(Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier) {
        Objects.requireNonNull(adpSupplier, "adpSupplier");
        return secrets(SecretsClient.builder()::build, adpSupplier);
    }

    static Supplier<? extends Secrets> secrets(Function<? super BasicAuthenticationDetailsProvider, ? extends Secrets> f,
                                               Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier) {
        Objects.requireNonNull(f, "f");
        Objects.requireNonNull(adpSupplier, "adpSupplier");
        return () -> f.apply(adpSupplier.get());
    }

}
