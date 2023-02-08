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

import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import io.helidon.common.Reflected;
import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Reflected
class ParticipantService {

    private static final System.Logger LOGGER = System.getLogger(ParticipantService.class.getName());

    private final LraCdiExtension lraCdiExtension;
    private final BeanManager beanManager;
    private final String nonJaxRsContextPath;
    private final Optional<URI> participantUri;
    private final Set<String> propagationPrefixes;

    private final Map<Class<?>, Participant> participants = new HashMap<>();

    @Inject
    ParticipantService(LraCdiExtension lraCdiExtension,
                       BeanManager beanManager,
                       @ConfigProperty(name = NonJaxRsResource.CONFIG_CONTEXT_PATH_KEY,
                               defaultValue = NonJaxRsResource.CONTEXT_PATH_DEFAULT) String nonJaxRsContextPath,
                       @ConfigProperty(name = "mp.lra.participant.url") Optional<URI> participantUri,
                       @ConfigProperty(
                               name = CoordinatorClient.CONF_KEY_COORDINATOR_HEADERS_PROPAGATION_PREFIX,
                               defaultValue = ""
                       ) Set<String> propagationPrefixes) {
        this.lraCdiExtension = lraCdiExtension;
        this.beanManager = beanManager;
        this.nonJaxRsContextPath = nonJaxRsContextPath;
        this.participantUri = participantUri;
        this.propagationPrefixes = propagationPrefixes;
    }

    Participant participant(URI defaultBaseUri, Class<?> clazz) {
        return participants.computeIfAbsent(clazz, c ->
                // configured value overrides base uri
                new ParticipantImpl(participantUri.orElse(defaultBaseUri), nonJaxRsContextPath, c));
    }

    PropagatedHeaders prepareCustomHeaderPropagation(Map<String, List<String>> headers) {
        PropagatedHeaders propagatedHeaders = PropagatedHeaders.create(propagationPrefixes);
        // Scan for compatible headers
        propagatedHeaders.scan(headers);
        return propagatedHeaders;
    }

    /**
     * Participant ID is expected to be classFqdn#methodName.
     */
    Optional<?> invoke(String classFqdn,
                       String methodName,
                       URI lraId,
                       Object secondParam,
                       PropagatedHeaders propagatedHeaders) {
        Class<?> clazz;
        try {
            clazz = Class.forName(classFqdn);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cant locate participant method: " + classFqdn + "#" + methodName, e);
        }

        try {
            Bean<?> bean = lraCdiExtension.lraCdiBeanReferences().get(clazz);
            Objects.requireNonNull(bean, () -> "Missing bean reference for participant method: " + classFqdn + "#" + methodName);
            Method method = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Cant find participant method " + methodName
                            + " with participant method: " + classFqdn + "#" + methodName));

            int paramCount = method.getParameters().length;

            setHeaderPropagationContext(propagatedHeaders);

            Object result = method.invoke(LraCdiExtension.lookup(bean, beanManager),
                    Stream.of(lraId, secondParam).limit(paramCount).toArray());

            return fixResult(result);

        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cant invoke participant method " + methodName
                    + " with participant method: " + classFqdn + "#" + methodName, e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof WebApplicationException wae) {
                return Optional.ofNullable(wae.getResponse());
            } else if (e.getTargetException() instanceof RuntimeException re){
                throw re;
            } else {
                throw new RuntimeException(e.getTargetException());
            }
        } catch (Throwable t) {
            LOGGER.log(Level.ERROR, "Un-caught exception in non-jax-rs LRA method "
                    + classFqdn + "#" + methodName
                    + " LRA id: " + lraId,
                       t);
            throw t;
        }
    }

    private void setHeaderPropagationContext(PropagatedHeaders propagatedHeaders) {
        String key = PropagatedHeaders.class.getName();
        Contexts.context()
                .ifPresent(context -> context.register(key, propagatedHeaders));
    }

    private Optional<?> fixResult(Object result) {
        if (result == null) {
            return Optional.empty();
        } else if (result instanceof Optional<?> opt) {
            return opt;
        } else if (result instanceof Response resp) {
            return Optional.of(resp);
        } else if (result instanceof CompletionStage<?> cs) {
            try {
                return Optional.ofNullable(cs.toCompletableFuture().get());
            } catch (Exception e) {
                throw new RuntimeException("Failed to get result from future", e);
            }
        }
        return Optional.of(result);
    }
}
