/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * The server supports authenticated, unauthenticated and anonymous users. Each request is created in the context
 * of some user.
 *
 * <p>{@link io.helidon.webserver.examples.tutorial.user.UserFilter UserFilter} is responsible for registration of valid
 * {@link io.helidon.webserver.examples.tutorial.user.User User} instance on the context of each request.
 */
package io.helidon.webserver.examples.tutorial.user;
