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
 * Vault authentication for Kubernetes.
 */
module io.helidon.integrations.vault.auths.k8s {
    requires io.helidon.integrations.vault;
    requires io.helidon.webclient;
    requires io.helidon.faulttolerance;
    requires io.helidon.integrations.common.rest;
    requires io.helidon.integrations.vault.auths.common;
    requires java.logging;

    exports io.helidon.integrations.vault.auths.k8s;

    provides io.helidon.integrations.vault.spi.VaultAuth
            with io.helidon.integrations.vault.auths.k8s.K8sVaultAuth;

    provides io.helidon.integrations.vault.spi.AuthMethodProvider
            with io.helidon.integrations.vault.auths.k8s.K8sAuthProvider;

    provides io.helidon.integrations.vault.spi.InjectionProvider
            with io.helidon.integrations.vault.auths.k8s.K8sAuthProvider;
}