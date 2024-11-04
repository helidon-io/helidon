/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Maven plugin for Helidon Service Inject.
 * <p>
 * This plugin should be used by the application - i.e. the actual microservice that is going to be deployed and started.
 * This plugin will not help when used on a library.
 * <p>
 * The plugin generates application binding (mapping of services to injection points they satisfy), and application main class
 * to avoid lookups (binding), and reflection and resources discovery from classpath (main class).
 */
package io.helidon.service.inject.maven.plugin;
