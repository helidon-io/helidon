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

package io.helidon.integrations.oci.atp;

import io.helidon.integrations.common.rest.ApiOptionalResponse;

/**
 * Blocking OCI ATP API.
 * All methods block the current thread. This implementation is not suitable for reactive programming.
 * Use {@link io.helidon.integrations.oci.atp.OciAutonomousDBRx} in reactive code.
 */
public interface OciAutonomousDB {
    /**
     * Create a blocking ATP integration from its reactive counterpart.
     * When running within an injection capable environment (such as CDI), instances of this
     * class can be injected.
     *
     * @param reactive reactive OCI ATP
     * @return blocking OCI ATP
     */
    static OciAutonomousDB create(OciAutonomousDBRx reactive) {
        return new OciAutonomousDBImpl(reactive);
    }

    /**
     * Gets the metadata and body of Wallet.
     *
     * @param request get object request
     * @return future with response or error
     */
    ApiOptionalResponse<GenerateAutonomousDatabaseWallet.Response> generateWallet(
            GenerateAutonomousDatabaseWalletRx.Request request);
}
