/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.http;

import java.util.Map;

import io.helidon.json.JsonObject;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.spi.ErrorHandlerProvider;

/**
 * Example of an HTTP error handler to have an easy approach to returning well-formatted error messages
 * for business exceptions.
 */
@SuppressWarnings("deprecation")
@Service.Singleton
class QuickstartErrorHandler implements ErrorHandlerProvider<QuickstartException> {
    @Override
    public Class<QuickstartException> errorType() {
        return QuickstartException.class;
    }

    @Override
    public ErrorHandler<QuickstartException> create() {
        return (req, res, t) -> {
            JsonObject jsonErrorObject = JsonObject.builder()
                    .set("error", t.getMessage())
                    .build();
            res.status(t.status())
                    .send(jsonErrorObject);
        };
    }
}
