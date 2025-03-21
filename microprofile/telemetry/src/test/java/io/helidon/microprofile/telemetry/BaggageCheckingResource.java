/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.telemetry;

import java.util.StringJoiner;

import io.opentelemetry.api.baggage.Baggage;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path(BaggageCheckingResource.PATH)
public class BaggageCheckingResource {

    static final String PATH = "/baggage";

    @GET
    public String extractBaggageSettings() {
        StringJoiner joiner = new StringJoiner(",");
        Baggage.current().forEach((key, baggageEntry) -> joiner.add(key + "=" + baggageEntry.getValue()));
        return joiner.toString();
    }
}
