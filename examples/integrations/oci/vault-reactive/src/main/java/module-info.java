/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
 * Example of OCI Vault integration in a reactive application.
 */
module io.helidon.examples.integrations.oci.vault.reactive {
    requires io.helidon.webserver;

    requires oci.java.sdk.keymanagement;
    requires oci.java.sdk.secrets;
    requires oci.java.sdk.vault;
    requires oci.java.sdk.common;

    exports io.helidon.examples.integrations.oci.vault.reactive;
}