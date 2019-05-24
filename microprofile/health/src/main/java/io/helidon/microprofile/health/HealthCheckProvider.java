/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.health;

import java.util.List;

import org.eclipse.microprofile.health.HealthCheck;

/**
 * A provider of {@link HealthCheck} instances.
 * <p>
 * Instances of {@link HealthCheckProvider} are discovered by the {@link HealthMpService}
 * using the {@link io.helidon.common.serviceloader.HelidonServiceLoader} and all of the
 * {@link HealthCheck} instances are added to the health endpoint.
 */
public interface HealthCheckProvider {
    /**
     * Return the provided {@link org.eclipse.microprofile.health.HealthCheck}s.
     *
     * @return  the {@link org.eclipse.microprofile.health.HealthCheck}s
     */
    List<HealthCheck> healthChecks();
}
