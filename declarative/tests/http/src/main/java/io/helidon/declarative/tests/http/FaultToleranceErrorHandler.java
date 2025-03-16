/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.faulttolerance.FaultToleranceException;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.spi.ErrorHandlerProvider;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

@Service.Singleton
class FaultToleranceErrorHandler implements ErrorHandlerProvider<FaultToleranceException> {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @Override
    public Class<FaultToleranceException> errorType() {
        return FaultToleranceException.class;
    }

    @Override
    public ErrorHandler<FaultToleranceException> create() {
        return (req, res, t) -> {
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", t.getMessage())
                    .build();

            res.status(Status.SERVICE_UNAVAILABLE_503)
                    .send(jsonErrorObject);
        };
    }
}
