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
 * Example of integration with OCI object storage in a CDI application.
 */
module io.helidon.examples.integrations.oci.objectstorage.cdi {
    requires java.ws.rs;
    requires java.json.bind;
    requires jakarta.inject.api;
    requires microprofile.config.api;

    requires io.helidon.config.yaml;
    requires io.helidon.common.http;
    requires io.helidon.integrations.common.rest;
    requires io.helidon.integrations.oci.objectstorage;
    requires io.helidon.microprofile.cdi;

    exports io.helidon.examples.integrations.oci.objectstorage.cdi;

    opens io.helidon.examples.integrations.oci.objectstorage.cdi to weld.core.impl, io.helidon.microprofile.cdi;
}