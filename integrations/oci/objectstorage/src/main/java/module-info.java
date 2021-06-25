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
 * OCI Object Storage integration.
 *
 * @see io.helidon.integrations.oci.objectstorage.OciObjectStorage
 * @see io.helidon.integrations.oci.objectstorage.OciObjectStorageRx
 */
module io.helidon.integrations.oci.objectstorage {
    requires transitive java.json;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.integrations.oci.connect;
    requires transitive io.helidon.config;

    requires io.helidon.integrations.common.rest;
    requires io.helidon.common.http;
    requires io.helidon.health;
    requires io.helidon.health.common;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires java.logging;

    exports io.helidon.integrations.oci.objectstorage;

    // this is the intended usage, deprecation is to warn about accidental usage in code
    //noinspection deprecation
    provides io.helidon.integrations.oci.connect.spi.InjectionProvider
            with io.helidon.integrations.oci.objectstorage.OciObjectStorageInjectionProvider;

    opens io.helidon.integrations.oci.objectstorage to weld.core.impl, io.helidon.microprofile.cdi;
}