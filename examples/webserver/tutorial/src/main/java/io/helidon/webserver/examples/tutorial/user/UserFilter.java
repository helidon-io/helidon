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

package io.helidon.webserver.examples.tutorial.user;

import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * If used as a {@link Routing Routing} {@link Handler} then assign valid {@link User} instance on the request
 * {@link io.helidon.common.context.Context context}.
 */
public class UserFilter implements Handler {

    @Override
    public void accept(ServerRequest req, ServerResponse res) {
        // Register as a supplier. Thanks to it, user instance is resolved ONLY if it is requested in downstream handlers.
        req.context().supply(User.class,
                             () -> req.headers()
                                      .cookies()
                                      .first("Unauthenticated-User-Alias")
                                      .map(User::new)
                                      .orElse(User.ANONYMOUS));
        req.next();
    }
}
