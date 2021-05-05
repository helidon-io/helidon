/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.idcs.mapper;

import java.util.Objects;

/**
 * IDCS multitenancy context used by the mappers.
 */
public final class IdcsMtContext {
    private final String tenantId;
    private final String appId;

    /**
     * Create new context with the specified ids.
     * @param tenantId The IDCS tenancy id.
     * @param appId The IDCS app ID.
     */
    public IdcsMtContext(final String tenantId, final String appId) {
        this.tenantId = tenantId;
        this.appId = appId;
    }

    /**
     * IDCS Tenancy ID.
     * @return idcs tenancy ID
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * IDCS Application ID.
     * @return idcs application ID
     */
    public String appId() {
        return appId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IdcsMtContext that = (IdcsMtContext) o;
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(appId, that.appId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, appId);
    }

    @Override
    public String toString() {
        return "IdcsMtContext{"
                + "tenantId='" + tenantId + '\''
                + ", appId='" + appId + '\''
                + '}';
    }
}
