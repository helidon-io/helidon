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

/**
 * OCI Objectstoragesupport module.
 */
module io.helidon.integrations.oci.objectstorage {
    requires java.logging;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires jakarta.interceptor.api;
    requires io.helidon.config;
    requires io.helidon.config.mp;

    requires oci.java.sdk.common;
    requires com.google.common;
    requires oci.java.sdk.objectstorage.generated;
    requires io.helidon.integrations.oci;

    exports io.helidon.integrations.oci.objectstorage;

    opens io.helidon.integrations.oci.objectstorage to weld.core.impl, io.helidon.microprofile.cdi;
}
