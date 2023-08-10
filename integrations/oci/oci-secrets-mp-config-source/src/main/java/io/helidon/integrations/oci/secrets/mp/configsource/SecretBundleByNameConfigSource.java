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

import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.oracle.bmc.secrets.Secrets;

import static io.helidon.integrations.oci.secrets.mp.configsource.Guards.guardWithAcceptPattern;
import static io.helidon.integrations.oci.secrets.mp.configsource.SecretBundleContentDetailsFunctions.secretBundleContentDetailsByName;

final class SecretBundleByNameConfigSource extends AbstractSecretBundleConfigSource {

    private SecretBundleByNameConfigSource() {
        this(null, null, null);
    }

    SecretBundleByNameConfigSource(Pattern acceptPattern, String vaultOcid, Supplier<? extends Secrets> ss) {
        super(guardWithAcceptPattern(secretBundleContentDetailsByName(vaultOcid, ss), acceptPattern), ss);
    }

}
