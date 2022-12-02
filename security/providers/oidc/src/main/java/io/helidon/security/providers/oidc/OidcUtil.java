/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc;

import io.helidon.common.http.FormParams;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;

class OidcUtil {

    private OidcUtil() {
    }

    static void updateRequest(OidcConfig.RequestType type, TenantConfig tenantConfig, FormParams.Builder form) {
        if (type == OidcConfig.RequestType.CODE_TO_TOKEN
                && tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_SECRET_POST) {
            form.add("client_id", tenantConfig.clientId());
            form.add("client_secret", tenantConfig.clientSecret());
        }
    }

}
