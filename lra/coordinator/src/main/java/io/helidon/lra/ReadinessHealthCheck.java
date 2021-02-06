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
package io.helidon.lra;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@Readiness
@ApplicationScoped
public class ReadinessHealthCheck implements HealthCheck {

    @Inject
    ReadinessHealthCheck() {
    }

    @Override
    public HealthCheckResponse call() {
        if (false) { //todo check db logging and messaging factories are ready
            return HealthCheckResponse.named("LRAReadinessDown")
                    .down()
                    .withData("databaseconnections", "not ready")
                    .build();
        } else return HealthCheckResponse.named("LRAReadiness")
                .up()
                .withData("databaseconnections", "ready")
                .build();
    }
}
