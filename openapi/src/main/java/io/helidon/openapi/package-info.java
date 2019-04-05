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
 *
 */
/**
 * Helidon SE OpenAPI Support.
 * <p>
 * Use {@link OpenAPISupport} and its {@code Builder} to include support for
 * OpenAPI in your application.
 * <p>
 * Because Helidon SE does not use annotation processing to identify endpoints,
 * you need to provide the OpenAPI information for your application yourself.
 * You can provide a static OpenAPI document or you can implement and specify
 * your own model processing class that provides the data needed to build the
 * OpenAPI document.
 */
package io.helidon.openapi;
