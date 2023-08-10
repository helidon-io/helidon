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

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleByNameRequest;

final class SecretBundleContentDetailsFunctions {

    private SecretBundleContentDetailsFunctions() {
        super();
    }

    @SuppressWarnings({"checkstyle:linelength"})
    static Function<String, SecretBundleContentDetails> secretBundleContentDetailsByName(String vaultId,
                                                                                         Supplier<? extends Secrets> ss) {
        return secretBundleContentDetailsByName(vaultId,
                                                GetSecretBundleByNameRequest::builder,
                                                UnaryOperator.identity(),
                                                r -> ss.get().getSecretBundleByName(r).getSecretBundle().getSecretBundleContent());
    }

    @SuppressWarnings({"checkstyle:linelength"})
    static Function<String, SecretBundleContentDetails> secretBundleContentDetailsByName(String vaultId,
                                                                                         Supplier<? extends GetSecretBundleByNameRequest.Builder> bs,
                                                                                         UnaryOperator<GetSecretBundleByNameRequest.Builder> op,
                                                                                         Function<? super GetSecretBundleByNameRequest, ? extends SecretBundleContentDetails> f) {
        return pn -> {
            if (pn == null || pn.isBlank()) {
                return null;
            }
            var b = bs.get(); // e.g. GetSecretBundleByNameRequest.builder();
            if (vaultId != null) {
                b.vaultId(vaultId);
            }
            b.secretName(pn);
            return f.apply(op.apply(b).build());
        };
    }

}
