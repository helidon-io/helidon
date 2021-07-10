/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.lra.tck.service.spi.LRACallbackException;
import org.eclipse.microprofile.lra.tck.service.spi.LRARecoveryService;

public class HelidonLRARecoveryService implements LRARecoveryService {

    private static final Logger LOGGER = Logger.getLogger(HelidonLRARecoveryService.class.getName());

    private static final String coordinatorUrl =
            System.getProperty("lra.coordinator.url", CoordinatorDeployer.LOCAL_COORDINATOR_URL);

    @Override
    public void waitForCallbacks(URI lraId) {
        if (CoordinatorDeployer.LOCAL_COORDINATOR_URL.equals(coordinatorUrl)) {
            // Helidon coordinator has simple recovery loop
            // Narayana does recovery with backoff, this would take ages
            try {
                this.waitForRecovery(lraId);
            } catch (LRACallbackException e) {
                LOGGER.log(Level.WARNING, "Wait for callback failed.", e);
            }
        }
    }

    @Override
    public void waitForRecovery(URI lraId) throws LRACallbackException {
        int counter = 0;

        do {
            if (counter > 9) return;
            LOGGER.info("Recovery attempt #" + ++counter);
        } while (!waitForEndPhaseReplay(lraId));
        LOGGER.info("LRA " + lraId + " has finished the recovery");

    }

    @Override
    public boolean waitForEndPhaseReplay(URI lraId) {
        Client client = ClientBuilder.newClient();
        try {

            Response response = client
                    .target(coordinatorUrl)
                    .path("recovery")
                    .request()
                    .get();

            String recoveringLras = response.readEntity(String.class);
            response.close();
            if (recoveringLras.contains(lraId.toASCIIString())) {
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
}