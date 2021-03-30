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

import org.eclipse.microprofile.lra.tck.service.spi.LRARecoveryService;

public class HelidonLRARecoveryService implements LRARecoveryService {

    @Override
    public void waitForCallbacks(URI lraId) {
    }

    @Override
    public boolean waitForEndPhaseReplay(URI lraId) {
        return true;
    }
}

/*
public class HelidonLRARecoveryService implements LRARecoveryService {
    private static final Logger log = Logger.getLogger(NarayanaLRARecovery.class);

    /*
     * A bit of hacking to change the internals of annotations defined in LRA TCK.
     * There is need to adjust timeout defined on the annotation definition.
     * /
static {
        String[] resourceClassNames = new String[]{
        "org.eclipse.microprofile.lra.tck.participant.api.LraResource",
        "org.eclipse.microprofile.lra.tck.participant.api.RecoveryResource"};
        for(String resourceClassName: resourceClassNames) {
        try {
        Class<?> clazz = Class.forName(resourceClassName);
        LRAAnnotationAdjuster.processWithClass(clazz);
        } catch (ClassNotFoundException e) {
        log.debugf("Cannot load class %s to adjust LRA annotation on the class", resourceClassName);
        }
        }
        }

@Override
public void waitForCallbacks(URI lraId) {
//        new Throwable("LRARecovery.waitForCallbacks").printStackTrace();
        // no action needed
        }

@Override
public boolean waitForEndPhaseReplay(URI lraId) {
//        new Throwable("LRARecovery.waitForEndPhaseReplay just sleeping").printStackTrace();
//        sleep();
        if (!recoverLRAs(lraId)) {
        // first recovery scan probably collided with periodic recovery which started
        // before the test execution so try once more
        return recoverLRAs(lraId);
        }
        return true;
        }

private void sleep() {
        try {
        Thread.sleep(5 * 1000);
        } catch (InterruptedException e) {
        e.printStackTrace();
        }
        }

 */

 /*
 * Invokes LRA coordinator recovery REST endpoint and returns whether the recovery of intended LRAs happened
 *
 * @param lraId the LRA id of the LRA that is intended to be recovered
 * @return true the intended LRA recovered, false otherwise
 * /
private boolean recoverLRAs(URI lraId) {
/ /        new Throwable("LRARecovery.recoverLRAs").printStackTrace();
        sleep();
        if(true) return true;
        // trigger a recovery scan
        Client recoveryCoordinatorClient = ClientBuilder.newClient();

        try {
        URI lraCoordinatorUri = new URI("http://localhost:8080/"); // LRAConstants.getLRACoordinatorUri(lraId);
        URI recoveryCoordinatorUri = UriBuilder.fromUri(lraCoordinatorUri)
        .path("lra-coordinator").build();
//                    .path(RECOVERY_COORDINATOR_PATH_NAME).build();
        WebTarget recoveryTarget = recoveryCoordinatorClient.target(recoveryCoordinatorUri);

        // send the request to the recovery coordinator
        Response response = recoveryTarget.request().get();
        String json = response.readEntity(String.class);
        response.close();

        if (json.contains(lraId.toASCIIString())) {
        // intended LRA didn't recover
        return false;
        }

        return true;
        } catch (Exception e) {
        return false;
        } finally {
        recoveryCoordinatorClient.close();
        }
        }
        }
 */