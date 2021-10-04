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
package io.helidon.microprofile.lra;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.helidon.common.Reflected;
import io.helidon.common.reactive.Single;
import io.helidon.lra.coordinator.client.Participant;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Reflected
class ParticipantService {

    private static final Logger LOGGER = Logger.getLogger(ParticipantService.class.getName());

    private final LraCdiExtension lraCdiExtension;
    private final BeanManager beanManager;
    private final String nonJaxRsContextPath;
    private final Optional<URI> participantUri;

    private final Map<Class<?>, Participant> participants = new HashMap<>();

    @Inject
    ParticipantService(LraCdiExtension lraCdiExtension,
                       BeanManager beanManager,
                       @ConfigProperty(name = NonJaxRsResource.CONFIG_CONTEXT_PATH_KEY,
                               defaultValue = NonJaxRsResource.CONTEXT_PATH_DEFAULT) String nonJaxRsContextPath,
                       @ConfigProperty(name = "mp.lra.participant.url") Optional<URI> participantUri) {
        this.lraCdiExtension = lraCdiExtension;
        this.beanManager = beanManager;
        this.nonJaxRsContextPath = nonJaxRsContextPath;
        this.participantUri = participantUri;
    }

    Participant participant(URI defaultBaseUri, Class<?> clazz) {
        return participants.computeIfAbsent(clazz, c ->
                // configured value overrides base uri
                new ParticipantImpl(participantUri.orElse(defaultBaseUri), nonJaxRsContextPath, c));
    }

    /**
     * Participant ID is expected to be classFqdn#methodName.
     */
    Single<Optional<?>> invoke(String classFqdn, String methodName, URI lraId, Object secondParam) {
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

            Object result = method.invoke(LraCdiExtension.lookup(bean, beanManager),
                    Stream.of(lraId, secondParam).limit(paramCount).toArray());

            return resultToSingle(result);

        } catch (IllegalAccessException e) {
            return Single.error(new RuntimeException("Cant invoke participant method " + methodName
                    + " with participant method: " + classFqdn + "#" + methodName, e));
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof WebApplicationException) {
                return Single.just(Optional.ofNullable(((WebApplicationException) e.getTargetException()).getResponse()));
            } else {
                return Single.error(e.getTargetException());
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, t, () -> "Un-caught exception in non-jax-rs LRA method "
                    + classFqdn + "#" + methodName
                    + " LRA id: " + lraId);
            return Single.error(t);
        }
    }

    private Single<Optional<?>> resultToSingle(Object result) {
        if (result == null) {
            return Single.just(Optional.empty());
        } else if (result instanceof Response) {
            return Single.just((Response) result)
                    .map(this::optionalMapper);
        } else if (result instanceof Single) {
            return ((Single<?>) result)
                    .map(this::optionalMapper);
        } else if (result instanceof CompletionStage) {
            return Single.create(((CompletionStage<?>) result).thenApply(this::optionalMapper));
        } else {
            return Single.just(optionalMapper(result));
        }
    }

    private Optional<?> optionalMapper(Object item) {
        if (item == null) {
            return Optional.empty();
        } else if (item instanceof Optional) {
            return (Optional<?>) item;
        } else {
            return Optional.of(item);
        }
    }
}
