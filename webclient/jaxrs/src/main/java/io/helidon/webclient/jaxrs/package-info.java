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
/**
 * Helidon support for JAX-RS (Jersey) client.
 * You can create the JAX-RS client as usual using {@link javax.ws.rs.client.ClientBuilder#newBuilder()}
 * and {@link javax.ws.rs.client.ClientBuilder#newClient()}.
 * <p>
 * If you want to configure defaults for asynchronous executor service,
 *  you can use {@link io.helidon.webclient.jaxrs.JaxRsClient#configureDefaults(io.helidon.config.Config)}
 *  or {@link io.helidon.webclient.jaxrs.JaxRsClient#defaultExecutor(java.util.function.Supplier)}.
 *
 * @see io.helidon.webclient.jaxrs.JaxRsClient
 */
package io.helidon.webclient.jaxrs;
