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

package io.helidon.microprofile.lra;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.common.Reflected;
import io.helidon.lra.coordinator.client.CoordinatorClient;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.jboss.jandex.AnnotationInstance;

import static io.helidon.lra.coordinator.client.CoordinatorClient.CONF_KEY_COORDINATOR_TIMEOUT;
import static io.helidon.lra.coordinator.client.CoordinatorClient.CONF_KEY_COORDINATOR_TIMEOUT_UNIT;

@Reflected
class HandlerService {

    private static final Map<String, AnnotationHandler.HandlerMaker> HANDLER_SUPPLIERS =
            Map.of(
                    LRA.class.getName(), LraAnnotationHandler::new,
                    Leave.class.getName(), (a, c, i, p, t) -> new LeaveAnnotationHandler(c, p),
                    Status.class.getName(), (a, c, i, p, t) -> new NoopAnnotationHandler(p),
                    AfterLRA.class.getName(), (a, c, i, p, t) -> new NoopAnnotationHandler(p)
            );

    private static final Set<String> STAND_ALONE_ANNOTATIONS = Set.of(
            Status.class.getName(),
            Complete.class.getName(),
            Compensate.class.getName(),
            AfterLRA.class.getName(),
            Forget.class.getName()
    );

    private final CoordinatorClient coordinatorClient;
    private final InspectionService inspectionService;
    private final ParticipantService participantService;
    private final Map<Method, List<AnnotationHandler>> handlerCache = new ConcurrentHashMap<>();
    private final boolean propagate;
    private final Duration coordinatorTimeout;

    @Inject
    HandlerService(CoordinatorClient coordinatorClient,
                   InspectionService inspectionService,
                   ParticipantService participantService,
                   @ConfigProperty(name = "mp.lra.propagation.active", defaultValue = "true")
                           boolean propagate,
                   @ConfigProperty(name = CONF_KEY_COORDINATOR_TIMEOUT, defaultValue = "30")
                           Long coordinatorTimeout,
                   @ConfigProperty(name = CONF_KEY_COORDINATOR_TIMEOUT_UNIT, defaultValue = "SECONDS")
                           TimeUnit coordinatorTimeoutUnit) {
        this.coordinatorClient = coordinatorClient;
        this.inspectionService = inspectionService;
        this.participantService = participantService;
        this.propagate = propagate;
        this.coordinatorTimeout = Duration.of(coordinatorTimeout, coordinatorTimeoutUnit.toChronoUnit());
    }

    List<AnnotationHandler> getHandlers(Method method) {
        return handlerCache.computeIfAbsent(method, this::createHandlers);
    }

    private List<AnnotationHandler> createHandlers(Method m) {
        Set<AnnotationInstance> lraAnnotations = inspectionService.lookUpLraAnnotations(m);
        if (lraAnnotations.isEmpty()) {
            return List.of(new NonLraAnnotationHandler(propagate, participantService));
        }

        if (lraAnnotations.stream()
                .map(a -> a.name().toString())
                .anyMatch(Leave.class.getName()::equals)) {
            return List.of(new LeaveAnnotationHandler(coordinatorClient, participantService));
        }

        if (lraAnnotations.stream()
                .map(a -> a.name().toString())
                .anyMatch(STAND_ALONE_ANNOTATIONS::contains)) {
            // Status beats all others
            return List.of(new NoopAnnotationHandler(participantService));
        }

        return lraAnnotations.stream().map(lraAnnotation -> {
                    var handlerMaker =
                            HANDLER_SUPPLIERS.get(lraAnnotation.name().toString());

                    if (handlerMaker == null) {
                        // Non LRA annotation on LRA method, skipping
                        return null;
                    }
                    return handlerMaker.make(lraAnnotation,
                            coordinatorClient,
                            inspectionService,
                            participantService,
                            coordinatorTimeout);
                }).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
