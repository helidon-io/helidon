/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
 * Helidon WebClient Metrics Support.
 *
 * <p>{@link io.helidon.webclient.metrics.WebClientTransportMetrics} records HTTP transport meters using
 * {@code role=client}. It is configured independently of the request metrics in
 * {@link io.helidon.webclient.metrics.WebClientMetrics}, using the {@code http-metrics} service name.
 */
package io.helidon.webclient.metrics;
