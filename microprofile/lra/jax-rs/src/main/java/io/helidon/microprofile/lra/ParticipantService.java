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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.helidon.common.Reflected;
import io.helidon.common.context.Contexts;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriPath;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.http.ServerRequest;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;

@Reflected
class ParticipantService {

    static final String CONFIG_PARTICIPANT_URL_KEY = "mp.lra.participant.url";
    static final String CONFIG_PARTICIPANT_ENDPOINTS_KEY = "mp.lra.participant.endpoints";

    private static final String MISSING_PARTICIPANT_URL_MESSAGE =
            "Configuration property " + CONFIG_PARTICIPANT_URL_KEY + " is required for LRA participant callbacks";
    private static final System.Logger LOGGER = System.getLogger(ParticipantService.class.getName());

    private final LraCdiExtension lraCdiExtension;
    private final BeanManager beanManager;
    private final String nonJaxRsContextPath;
    private final Optional<URI> participantUri;
    private final Map<EndpointIdentity, ConfiguredEndpoint> participantEndpoints;
    private final Set<String> propagationPrefixes;
    private final NonJaxRsCallbackAuthenticator callbackAuthenticator;
    private final Map<ParticipantFactoryKey, ParticipantFactory> participantFactories = new ConcurrentHashMap<>();
    private final Map<Class<?>, ParticipantMetadata> participantMetadata = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<NonJaxRsCallbackMethod, Method>> nonJaxRsParticipantMethods =
            new ConcurrentHashMap<>();

    @Inject
    ParticipantService(LraCdiExtension lraCdiExtension,
                       BeanManager beanManager,
                       @ConfigProperty(name = NonJaxRsResource.CONFIG_CONTEXT_PATH_KEY,
                               defaultValue = NonJaxRsResource.CONTEXT_PATH_DEFAULT) String nonJaxRsContextPath,
                       @ConfigProperty(name = CONFIG_PARTICIPANT_URL_KEY) Optional<URI> participantUri,
                       @ConfigProperty(name = CONFIG_PARTICIPANT_ENDPOINTS_KEY)
                       Map<String, String> participantEndpointProperties,
                       @ConfigProperty(
                               name = CoordinatorClient.CONF_KEY_COORDINATOR_HEADERS_PROPAGATION_PREFIX,
                               defaultValue = ""
                       ) Set<String> propagationPrefixes,
                       NonJaxRsCallbackAuthenticator callbackAuthenticator) {
        this.lraCdiExtension = lraCdiExtension;
        this.beanManager = beanManager;
        this.nonJaxRsContextPath = nonJaxRsContextPath;
        this.participantUri = participantUri;
        this.participantEndpoints = configuredEndpoints(participantEndpointProperties);
        this.propagationPrefixes = propagationPrefixes;
        this.callbackAuthenticator = callbackAuthenticator;
    }

    ParticipantFactory participantFactory(Class<?> clazz) {
        ParticipantMetadata metadata = participantMetadata(clazz);
        Optional<RequestEndpoint> requestEndpoint = metadata.jaxRsCallbacks()
                ? Optional.of(requestEndpoint())
                : Optional.empty();
        ParticipantFactoryKey key = new ParticipantFactoryKey(clazz, requestEndpoint);
        return participantFactories.computeIfAbsent(key, factoryKey -> {
            CallbackBaseUris callbackBaseUris = callbackBaseUris(factoryKey.requestEndpoint());
            return new ParticipantFactory(callbackBaseUris.jaxRsBaseUri(),
                                          callbackBaseUris.nonJaxRsBaseUri(),
                                          nonJaxRsContextPath,
                                          factoryKey.participantClass(),
                                          metadata.methods());
        });
    }

    Participant participant(Class<?> clazz, URI lraId) {
        return participantFactory(clazz).participant(lraId, callbackAuthenticator);
    }

    Participant participant(ParticipantFactory participantFactory, URI lraId) {
        return participantFactory.participant(lraId, callbackAuthenticator);
    }

    void validateConfiguration() {
        validatedDefaultParticipantUri();
        participantEndpoints.values()
                .forEach(endpoint -> validatedParticipantUri(endpoint.uri(), endpoint.configKey()));
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
            Map<NonJaxRsCallbackMethod, Method> methods =
                    nonJaxRsParticipantMethods.computeIfAbsent(clazz, this::scanNonJaxRsParticipantMethods);
            Method method = Optional.ofNullable(methods.get(new NonJaxRsCallbackMethod(expectedAnnotation, methodName)))
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

    private static URI validatedParticipantUri(URI uri, String configKey) {
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new DeploymentException("Configuration property " + configKey
                                                  + " must be an absolute HTTP or HTTPS URI without user info,"
                                                  + " query, or fragment: " + uri);
        }
        return uri;
    }

    private static String normalizedApplicationPath(String path) {
        if (path == null || path.isBlank() || path.equals("/")) {
            return "/";
        }
        String normalized = path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizedEndpointApplicationPath(String path) {
        return normalizedApplicationPath(UriEncoding.decodeUri(path));
    }

    private static String normalizedDefaultApplicationPath(String path) {
        return normalizedApplicationPath(UriPath.create(path).path());
    }

    private static String requestApplicationPath(ServerRequest serverRequest) {
        var requestPath = serverRequest.path();
        String relativePath = requestPath.path();
        String absolutePath = requestPath.absolute().path();
        return normalizedApplicationPath(
                absolutePath.substring(0, absolutePath.length() - relativePath.length() + 1));
    }

    private static URI resolvePort(URI uri, int port) {
        if (uri.getPort() != 0) {
            return uri;
        }
        String rawAuthority = uri.getRawAuthority();
        int portSeparator = rawAuthority.lastIndexOf(':');
        return URI.create(uri.getScheme() + "://"
                                  + rawAuthority.substring(0, portSeparator + 1)
                                  + port
                                  + Optional.ofNullable(uri.getRawPath()).orElse(""));
    }

    private static Map<EndpointIdentity, ConfiguredEndpoint> configuredEndpoints(Map<String, String> properties) {
        Map<String, Map<String, String>> endpointDefinitions = new HashMap<>();
        properties.forEach((key, value) -> addEndpointProperty(endpointDefinitions, key, value));
        Map<EndpointIdentity, ConfiguredEndpoint> endpoints = new HashMap<>();
        endpointDefinitions.forEach((name, values) -> addConfiguredEndpoint(endpoints, name, values));
        return Map.copyOf(endpoints);
    }

    private static void addEndpointProperty(Map<String, Map<String, String>> endpoints,
                                            String key,
                                            String value) {
        int separator = key.lastIndexOf('.');
        if (separator < 1 || separator == key.length() - 1) {
            throw new DeploymentException("Configuration property " + CONFIG_PARTICIPANT_ENDPOINTS_KEY + "." + key
                                                  + " must identify an endpoint and property");
        }
        String endpointName = key.substring(0, separator);
        String propertyName = key.substring(separator + 1);
        switch (propertyName) {
            case "listener", "application-path", "url" -> {
                Map<String, String> endpoint = endpoints.get(endpointName);
                if (endpoint == null) {
                    endpoint = new HashMap<>();
                    endpoints.put(endpointName, endpoint);
                }
                endpoint.put(propertyName, value);
            }
            default -> throw new DeploymentException("Unknown configuration property "
                                                             + CONFIG_PARTICIPANT_ENDPOINTS_KEY + "." + key);
        }
    }

    private static void addConfiguredEndpoint(Map<EndpointIdentity, ConfiguredEndpoint> endpoints,
                                              String endpointName,
                                              Map<String, String> values) {
        String configKey = CONFIG_PARTICIPANT_ENDPOINTS_KEY + "." + endpointName;
        String listenerName = requiredEndpointValue(values, configKey, "listener");
        String applicationPath = requiredEndpointValue(values, configKey, "application-path");
        if (!applicationPath.startsWith("/")) {
            throw new DeploymentException("Configuration property " + configKey
                                                  + ".application-path must start with '/': " + applicationPath);
        }
        URI uri;
        try {
            uri = URI.create(requiredEndpointValue(values, configKey, "url"));
        } catch (IllegalArgumentException e) {
            throw new DeploymentException("Configuration property " + configKey + ".url must be a valid URI", e);
        }
        EndpointIdentity identity = new EndpointIdentity(listenerName,
                                                         normalizedEndpointApplicationPath(applicationPath));
        ConfiguredEndpoint previous = endpoints.put(identity, new ConfiguredEndpoint(configKey + ".url", uri));
        if (previous != null) {
            throw new DeploymentException("Duplicate LRA participant endpoint for listener " + listenerName
                                                  + " and application path " + applicationPath);
        }
    }

    private static String requiredEndpointValue(Map<String, String> values, String configKey, String key) {
        return Optional.ofNullable(values.get(key))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new DeploymentException("Configuration property " + configKey + "." + key
                                                                    + " is required"));
    }

    private static boolean hasJaxRsCallbacks(Class<?> participantClass,
                                             Map<Class<? extends Annotation>, Set<Method>> methods) {
        return methods.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != LRA.class)
                .map(entry -> entry.getValue().iterator().next())
                .anyMatch(method -> ParticipantImpl.jaxRsMethod(participantClass, method).isPresent());
    }

    private ParticipantMetadata participantMetadata(Class<?> clazz) {
        return participantMetadata.computeIfAbsent(clazz, participantClass -> {
            Map<Class<? extends Annotation>, Set<Method>> methods = ParticipantFactory.methods(participantClass);
            return new ParticipantMetadata(methods, hasJaxRsCallbacks(participantClass, methods));
        });
    }

    private Map<NonJaxRsCallbackMethod, Method> scanNonJaxRsParticipantMethods(Class<?> clazz) {
        Map<NonJaxRsCallbackMethod, Method> nonJaxRsMethods = new HashMap<>();
        Map<Class<? extends Annotation>, Set<Method>> methods = participantMetadata(clazz).methods();
        for (Map.Entry<Class<? extends Annotation>, Set<Method>> entry : methods.entrySet()) {
            Class<? extends Annotation> annotation = entry.getKey();
            if (!ParticipantImpl.NON_JAX_RS_PARTICIPANT_CALLBACK_ANNOTATIONS.contains(annotation)) {
                continue;
            }
            for (Method method : entry.getValue()) {
                if (ParticipantImpl.isNonJaxRsParticipantMethod(clazz, method)) {
                    nonJaxRsMethods.putIfAbsent(new NonJaxRsCallbackMethod(annotation, method.getName()), method);
                }
            }
        }
        return Map.copyOf(nonJaxRsMethods);
    }

    private URI validatedDefaultParticipantUri() {
        return validatedParticipantUri(
                participantUri.orElseThrow(() -> new DeploymentException(MISSING_PARTICIPANT_URL_MESSAGE)),
                CONFIG_PARTICIPANT_URL_KEY);
    }

    private URI validatedParticipantUriForRequest(URI uri, String configKey) {
        try {
            return validatedParticipantUri(uri, configKey);
        } catch (DeploymentException e) {
            throw new WebApplicationException(e.getMessage(), e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private CallbackBaseUris callbackBaseUris(Optional<RequestEndpoint> requestEndpoint) {
        URI configuredBaseUri = validatedParticipantUriForRequest(
                participantUri.orElseThrow(() -> new WebApplicationException(MISSING_PARTICIPANT_URL_MESSAGE,
                                                                              Response.Status.INTERNAL_SERVER_ERROR)),
                CONFIG_PARTICIPANT_URL_KEY);
        URI nonJaxRsBaseUri = configuredBaseUri.getPort() == 0
                ? resolvePort(configuredBaseUri, beanManager.getExtension(ServerCdiExtension.class).port())
                : configuredBaseUri;
        URI jaxRsBaseUri = requestEndpoint
                .map(this::jaxRsBaseUri)
                .orElse(nonJaxRsBaseUri);
        return new CallbackBaseUris(jaxRsBaseUri, nonJaxRsBaseUri);
    }

    private URI jaxRsBaseUri(RequestEndpoint requestEndpoint) {
        EndpointIdentity endpointIdentity = requestEndpoint.identity();
        ConfiguredEndpoint configuredEndpoint = participantEndpoints.get(endpointIdentity);
        if (configuredEndpoint != null) {
            return resolvePort(validatedParticipantUriForRequest(configuredEndpoint.uri(), configuredEndpoint.configKey()),
                               requestEndpoint.listenerPort());
        }

        URI configuredBaseUri = validatedParticipantUriForRequest(
                participantUri.orElseThrow(() -> new WebApplicationException(MISSING_PARTICIPANT_URL_MESSAGE,
                                                                              Response.Status.INTERNAL_SERVER_ERROR)),
                CONFIG_PARTICIPANT_URL_KEY);
        String defaultApplicationPath = normalizedDefaultApplicationPath(configuredBaseUri.getRawPath());
        if (!DEFAULT_SOCKET_NAME.equals(endpointIdentity.listenerName())
                || !defaultApplicationPath.equals(endpointIdentity.applicationPath())) {
            throw new WebApplicationException("No " + CONFIG_PARTICIPANT_ENDPOINTS_KEY
                                                      + " entry matches listener " + endpointIdentity.listenerName()
                                                      + " and application path "
                                                      + endpointIdentity.applicationPath()
                                                      + "; the default participant URL matches listener "
                                                      + DEFAULT_SOCKET_NAME + " and application path "
                                                      + defaultApplicationPath,
                                              Response.Status.INTERNAL_SERVER_ERROR);
        }
        return resolvePort(configuredBaseUri, requestEndpoint.listenerPort());
    }

    private RequestEndpoint requestEndpoint() {
        ServerRequest serverRequest = Contexts.context()
                .flatMap(context -> context.get(ServerRequest.class))
                .orElseThrow(() -> new WebApplicationException("Cannot resolve the Helidon server request",
                                                                Response.Status.INTERNAL_SERVER_ERROR));
        return new RequestEndpoint(
                new EndpointIdentity(serverRequest.listenerContext().config().name(),
                                     requestApplicationPath(serverRequest)),
                serverRequest.localPeer().port());
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

    private record NonJaxRsCallbackMethod(Class<? extends Annotation> annotation, String methodName) {
    }

    private record ParticipantFactoryKey(Class<?> participantClass, Optional<RequestEndpoint> requestEndpoint) {
    }

    private record ParticipantMetadata(Map<Class<? extends Annotation>, Set<Method>> methods, boolean jaxRsCallbacks) {
    }

    private record CallbackBaseUris(URI jaxRsBaseUri, URI nonJaxRsBaseUri) {
    }

    private record EndpointIdentity(String listenerName, String applicationPath) {
    }

    private record ConfiguredEndpoint(String configKey, URI uri) {
    }

    private record RequestEndpoint(EndpointIdentity identity, int listenerPort) {
    }
}
