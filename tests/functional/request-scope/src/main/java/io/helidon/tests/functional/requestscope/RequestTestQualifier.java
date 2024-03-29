/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.functional.requestscope;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
@TestQualifier
public class RequestTestQualifier {

    @Inject
    private TenantContext tenantContext;

    /**
     * A test method.
     *
     * @return tenant id
     * @throws Exception if error occurs
     */
    public String test() throws Exception {
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalTenantException("No tenant context");
        }
        return tenantId;
    }
}
