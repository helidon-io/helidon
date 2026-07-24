/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.helidon.common.Reflected;
import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Reflected
class ParticipantService {

    static final String CONFIG_PARTICIPANT_URL_KEY = "mp.lra.participant.url";

    private static final System.Logger LOGGER = System.getLogger(ParticipantService.class.getName());

    private final LraCdiExtension lraCdiExtension;
    private final BeanManager beanManager;
    private final String nonJaxRsContextPath;
    private final Optional<URI> participantUri;
    private final Set<String> propagationPrefixes;
    private final NonJaxRsCallbackAuthenticator callbackAuthenticator;
    private final Map<Class<?>, ParticipantFactory> participantFactories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<Class<? extends Annotation>, Set<Method>>> participantMethods =
            new ConcurrentHashMap<>();

    @Inject
    ParticipantService(LraCdiExtension lraCdiExtension,
                       BeanManager beanManager,
                       @ConfigProperty(name = NonJaxRsResource.CONFIG_CONTEXT_PATH_KEY,
                               defaultValue = NonJaxRsResource.CONTEXT_PATH_DEFAULT) String nonJaxRsContextPath,
                       @ConfigProperty(name = CONFIG_PARTICIPANT_URL_KEY) Optional<URI> participantUri,
                       @ConfigProperty(
                               name = CoordinatorClient.CONF_KEY_COORDINATOR_HEADERS_PROPAGATION_PREFIX,
                               defaultValue = ""
                       ) Set<String> propagationPrefixes,
                       NonJaxRsCallbackAuthenticator callbackAuthenticator) {
        this.lraCdiExtension = lraCdiExtension;
        this.beanManager = beanManager;
        this.nonJaxRsContextPath = nonJaxRsContextPath;
        this.participantUri = participantUri;
        this.propagationPrefixes = propagationPrefixes;
        this.callbackAuthenticator = callbackAuthenticator;
    }

    Participant participant(URI defaultBaseUri, Class<?> clazz, URI lraId) {
        Map<Class<? extends Annotation>, Set<Method>> methods =
                participantMethods.computeIfAbsent(clazz, ParticipantFactory::methods);
        if (participantUri.isEmpty()) {
            return new ParticipantFactory(defaultBaseUri, nonJaxRsContextPath, clazz, methods)
                    .participant(lraId, callbackAuthenticator);
        }
        return participantFactories.computeIfAbsent(clazz, participantClass -> {
            URI baseUri = participantUri.get();
            if (baseUri.getPort() == 0) {
                baseUri = UriBuilder.fromUri(baseUri)
                        .port(beanManager.getExtension(ServerCdiExtension.class).port())
                        .build();
            }
            return new ParticipantFactory(baseUri, nonJaxRsContextPath, participantClass, methods);
        })
                .participant(lraId, callbackAuthenticator);
    }

    void validateConfiguration() {
        if (participantUri.isEmpty()) {
            throw new DeploymentException("Configuration property " + CONFIG_PARTICIPANT_URL_KEY
                                                  + " is required for non-JAX-RS LRA participant callbacks"
                                                  + " and JAX-RS participants that use @Leave");
        }
        URI uri = participantUri.get();
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new DeploymentException("Configuration property " + CONFIG_PARTICIPANT_URL_KEY
                                                  + " must be an absolute HTTP or HTTPS URI without user info,"
                                                  + " query, or fragment: " + uri);
        }
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
                       Class<? extends Annotation> expectedAnnotation,
                       URI lraId,
                       Object secondParam,
                       PropagatedHeaders propagatedHeaders) {
        try {
            Map.Entry<Class<?>, Bean<?>> participant = lraCdiExtension.lraCdiBeanReferences()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().getName().equals(classFqdn))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Missing participant method: "
                                                                      + classFqdn + "#" + methodName));
            Class<?> clazz = participant.getKey();
            Bean<?> bean = participant.getValue();
            Method method = ParticipantImpl.scanForLRAMethods(clazz)
                    .getOrDefault(expectedAnnotation, Set.of())
                    .stream()
                    .filter(m -> m.getName().equals(methodName))
                    .filter(m -> ParticipantImpl.isNonJaxRsParticipantMethod(clazz, m))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Cant find participant method " + methodName
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
            } else if (e.getTargetException() instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e.getTargetException());
            }
        } catch (WebApplicationException e) {
            throw e;
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
