/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
 * Provider that can extract username from a (any) header.
 * This provider can also propagate username.
 * This can be used for cases when user/service is authenticated
 * on perimeter (e.g. we resolve client certificates on an apache instance and just get X-AUTH-USER header).
 * For more modern frameworks, we can expect a JWT (JSON web token) and that is processed by a dedicated security provider.
 */
package io.helidon.security.provider.header;
