/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.spi;

import java.util.Optional;

import io.helidon.integrations.oci.ImdsInstanceInfo;
import io.helidon.service.registry.Service;

/**
 * An OCI Imds discovery mechanism to provide some relevant instance details as a service in Helidon
 * service registry.
 */
@Service.Contract
public interface OciImdsInstanceInfo {

    /**
     * The Instance information from Imds, if it can be provided by this service.
     *
     * @return Instance Information retrieved from Imds
     */
    Optional<ImdsInstanceInfo> instanceInfo();
}
