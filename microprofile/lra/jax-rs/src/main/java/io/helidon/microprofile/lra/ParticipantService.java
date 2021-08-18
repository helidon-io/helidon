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
import java.util.stream.Stream;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import io.helidon.lra.coordinator.client.Participant;

import org.eclipse.microprofile.config.inject.ConfigProperty;

class ParticipantService {

    private final LraCdiExtension lraCdiExtension;
    private final BeanManager beanManager;
    private final Optional<URI> participantUri;

    private final Map<Class<?>, Participant> participants = new HashMap<>();

    @Inject
    ParticipantService(LraCdiExtension lraCdiExtension,
                              BeanManager beanManager,
                              @ConfigProperty(name = "mp.lra.participant.url") Optional<URI> participantUri) {
        this.lraCdiExtension = lraCdiExtension;
        this.beanManager = beanManager;
        this.participantUri = participantUri;
    }

    Participant participant(URI defaultBaseUri, Class<?> clazz) {
        return participants.computeIfAbsent(clazz, c ->
                // configured value overrides base uri
                new ParticipantImpl(participantUri.orElse(defaultBaseUri), c));
    }

    /**
     * Participant ID is expected to be classFqdn#methodName.
     */
    Object invoke(String classFqdn, String methodName, URI lraId, Object secondParam) throws InvocationTargetException {
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
            return method.invoke(LraCdiExtension.lookup(bean, beanManager),
                    Stream.of(lraId, secondParam).limit(paramCount).toArray());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cant invoke participant method " + methodName
                    + " with participant method: " + classFqdn + "#" + methodName, e);
        }
    }
}
