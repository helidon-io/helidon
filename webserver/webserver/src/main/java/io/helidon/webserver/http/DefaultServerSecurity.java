/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.Arrays;

import io.helidon.http.ForbiddenException;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.UnauthorizedException;

import static java.lang.System.Logger.Level.DEBUG;

class DefaultServerSecurity implements HttpSecurity {
    private static final System.Logger LOGGER = System.getLogger(DefaultServerSecurity.class.getName());

    @Override
    public boolean authenticate(ServerRequest request, ServerResponse response, boolean requiredHint)
            throws UnauthorizedException {
        if (requiredHint) {
            throw new HttpException("Not Authenticated", Status.UNAUTHORIZED_401);
        }
        return true;
    }

    @Override
    public boolean authorize(ServerRequest request, ServerResponse response, String... roleHint) throws ForbiddenException {
        if (roleHint.length != 0) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG,
                           "Requested: " + request.prologue() + ", but roles required: " + Arrays.toString(roleHint));
            }
            throw new ForbiddenException("This endpoint is restricted");
        }
        return true;
    }
}
