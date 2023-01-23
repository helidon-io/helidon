/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra.tck;

import java.io.StringReader;
import java.lang.System.Logger.Level;
import java.net.URI;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.lra.tck.service.spi.LRACallbackException;
import org.eclipse.microprofile.lra.tck.service.spi.LRARecoveryService;

public class HelidonLRARecoveryService implements LRARecoveryService {

    private static final System.Logger LOGGER = System.getLogger(HelidonLRARecoveryService.class.getName());

    @Override
    public void waitForCallbacks(URI lraId) {
        try {
            this.waitForRecovery(lraId);
        } catch (LRACallbackException e) {
            LOGGER.log(Level.WARNING, "Wait for callback failed.", e);
        }
    }

    @Override
    public void waitForRecovery(URI lraId) throws LRACallbackException {
        int counter = 0;

        do {
            if (counter > 15) return;
            LOGGER.log(Level.INFO, "Recovery attempt #" + ++counter + " of " + lraId);
        } while (!waitForEndPhaseReplay(lraId));
        LOGGER.log(Level.INFO, "LRA " + lraId + " has finished the recovery");

    }

    @Override
    public boolean waitForEndPhaseReplay(URI lraId) {
        Client client = ClientBuilder.newClient();
        String lraIdString = lraId.toASCIIString();
        URI coordinatorUri = coordinatorPath(lraId);
        LOGGER.log(Level.INFO, "Coordinator url for " + lraIdString + " is " + coordinatorUri.toASCIIString());
        try {
            Response response = client
                    .target(coordinatorUri)
                    .path("recovery")
                    .queryParam("lraId", lraIdString)
                    .request()
                    .get();

            String recoveringLras = response.readEntity(String.class);
            response.close();
            if (recoveringLras.contains(lraIdString)) {
                for (JsonValue jval : Json.createReader(new StringReader(recoveringLras)).readArray()) {
                    if (jval.getValueType() == JsonValue.ValueType.STRING) {
                        // Oracle TMM
                        return false;
                    }
                    JsonObject jsonLra = jval.asJsonObject();
                    if (lraIdString.equals(jsonLra.getString("lraId"))) {
                        boolean recovering = jsonLra.getBoolean("recovering");
                        LOGGER.log(Level.INFO, "LRA " + lraIdString + " is recovering: " + recovering);
                        return recovering;
                    }
                }
                // intended LRA is among those still waiting for recovering
                return false;
            }
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Error when accessing recovery endpoint.", e);
        } finally {
            client.close();
        }
        return true;
    }

    private URI coordinatorPath(URI lraId) {
        String path = lraId.toASCIIString();
        return UriBuilder.fromPath(path.substring(0, path.lastIndexOf('/'))).build();
    }
}