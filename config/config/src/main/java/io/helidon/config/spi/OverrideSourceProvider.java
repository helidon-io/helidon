/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
package io.helidon.config.spi;

/**
 * Java service loader service to provide a override source based on meta configuration.
 *
 * @deprecated Since 28.0.0, use the {@code io.helidon.config:helidon-config-overrides} module instead.
 * Implement {@link ConfigFilterProvider} for custom loading, or register built-in override rules:
 * <pre>{@code
 * import io.helidon.config.Config;
 * import io.helidon.config.overrides.OverrideConfigFilterProvider;
 *
 * Config config = Config.builder()
 *         .disableFilterServices()
 *         .addFilterProvider(OverrideConfigFilterProvider.builder()
 *                 .putOverrideExpression("service.*.port", "8080")
 *                 .build())
 *         .build();
 * }</pre>
 * This disables filter discovery; register any other required filters explicitly.
 * See {@link OverrideSource} for further migration considerations. No removal release or timing has been decided.
 */
@Deprecated(since = "28.0.0", forRemoval = true)
public interface OverrideSourceProvider extends MetaConfigurableProvider<OverrideSource> {
}
