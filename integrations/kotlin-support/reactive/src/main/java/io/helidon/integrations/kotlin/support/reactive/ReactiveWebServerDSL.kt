/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.kotlin.support.reactive

import io.helidon.common.pki.KeyConfig
import io.helidon.reactive.health.HealthSupport
import io.helidon.reactive.media.common.MediaContext
import io.helidon.reactive.webserver.cors.CorsSupport
import io.helidon.reactive.webserver.jersey.JerseySupport
import io.helidon.reactive.webserver.Routing
import io.helidon.reactive.webserver.RequestPredicate
import io.helidon.reactive.webserver.ServerConfiguration
import io.helidon.reactive.webserver.SocketConfiguration
import io.helidon.reactive.webserver.WebServer
import io.helidon.reactive.webserver.WebServer.builder
import io.helidon.reactive.webserver.WebServerTls
import io.helidon.security.providers.oidc.common.OidcConfig


/**
 * DSL for the builder for WebServer and support objects.
 */
fun webServer(block: WebServer.Builder.() -> Unit = {}): WebServer = builder().apply(block).build()

fun webServerTls(block: WebServerTls.Builder.() -> Unit = {}): WebServerTls = WebServerTls.builder().apply(block).build()

fun serverConfiguration(block: ServerConfiguration.Builder.() -> Unit = {}): ServerConfiguration =
    ServerConfiguration.builder().apply(block).build()

fun routing(block: Routing.Builder.() -> Unit = {}): Routing = Routing.builder().apply(block).build()

fun requestPredicate(block: RequestPredicate.() -> Unit = {}): RequestPredicate = RequestPredicate.create().apply(block)

fun jerseySupport(block: JerseySupport.Builder.() -> Unit = {}): JerseySupport =
    JerseySupport.builder().apply(block).build()

fun mediaContext(block: MediaContext.Builder.() -> Unit = {}): MediaContext = MediaContext.builder().apply(block).build()

fun socketConfiguration(block: SocketConfiguration.Builder.() -> Unit = {}): SocketConfiguration =
    SocketConfiguration.builder().apply(block).build()

fun keystoreBuilder(block: KeyConfig.KeystoreBuilder.() -> Unit = {}): KeyConfig =
    KeyConfig.keystoreBuilder().apply(block).build()

fun healthSupport(block: HealthSupport.Builder.() -> Unit = {}): HealthSupport = HealthSupport.builder().apply(block).build()

fun corsSupport(block: CorsSupport.Builder.() -> Unit = {}): CorsSupport = CorsSupport.builder().apply(block).build()

fun oidcConfig(block: OidcConfig.Builder.() -> Unit = {}): OidcConfig = OidcConfig.builder().apply(block).build()
