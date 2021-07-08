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
 *  
 */

/**
 * MicroProfile Long Running Actions Coordinator client spi.
 * <p>
 * Provides abstraction over various coordinators. Implementation of
 * {@link io.helidon.lra.coordinator.client.CoordinatorClient CoordinatorClient} is expected to be
 * an application scoped bean.
 * <p>
 * Coordinator client is expected to leverage with following configuration keys:
 *
 * <ul>
 * <li>{@link io.helidon.lra.coordinator.client.CoordinatorClient#CONF_KEY_COORDINATOR_URL mp.lra.coordinator.url}
 * URL of coordinator</li>
 * <li>{@link io.helidon.lra.coordinator.client.CoordinatorClient#CONF_KEY_COORDINATOR_TIMEOUT mp.lra.coordinator.timeout}
 * Timeout for coordinator calls</li>
 * <li>{@link io.helidon.lra.coordinator.client.CoordinatorClient#CONF_KEY_COORDINATOR_TIMEOUT_UNIT mp.lra.coordinator.timeout-unit}
 * Timeout unit for coordinator calls timeout</li>
 * </ul>
 */
package io.helidon.lra.coordinator.client;
