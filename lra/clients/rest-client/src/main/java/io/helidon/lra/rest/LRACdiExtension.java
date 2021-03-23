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
package io.helidon.lra.rest;

import org.jboss.jandex.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.util.AnnotationLiteral;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LRACdiExtension implements Extension {

    private ClassPathIndexer classPathIndexer = new ClassPathIndexer();
    private Index index;
    private final Map<String, LRAParticipant> participants = new HashMap<>();

    public void observe(@Observes AfterBeanDiscovery event, BeanManager beanManager) throws IOException, ClassNotFoundException {
        index = classPathIndexer.createIndex();

        List<AnnotationInstance> annotations = index.getAnnotations(DotName.createSimple("javax.ws.rs.Path"));

        for (AnnotationInstance annotation : annotations) {
            ClassInfo classInfo;
            AnnotationTarget target = annotation.target();

            if (target.kind().equals(AnnotationTarget.Kind.CLASS)) {
                classInfo = target.asClass();
            } else if (target.kind().equals(AnnotationTarget.Kind.METHOD)) {
                classInfo = target.asMethod().declaringClass();
            } else {
                continue;
            }

            LRAParticipant participant = getAsParticipant(classInfo);
            if (participant != null) {
                participants.put(participant.getJavaClass().getName(), participant);
                Set<Bean<?>> participantBeans = beanManager.getBeans(participant.getJavaClass(), new AnnotationLiteral<Any>() {});
                if (participantBeans.isEmpty()) {
                    // resource is not registered as managed bean so register a custom managed instance
                    try {
                        participant.setInstance(participant.getJavaClass().newInstance());
                    } catch (InstantiationException | IllegalAccessException e) {
                        // todo LRALogger.i18NLogger.error_cannotProcessParticipant(e);
                    }
                }
            }
        }

        event.addBean()
            .read(beanManager.createAnnotatedType(LRAParticipantRegistry.class))
            .beanClass(LRAParticipantRegistry.class)
            .scope(ApplicationScoped.class)
            .createWith(context -> new LRAParticipantRegistry(participants));
    }

    /**
     * Collects all non-JAX-RS participant methods in the defined Java class
     *
     * @param classInfo a Jandex class info of the class to be scanned
     * @return Collected methods wrapped in {@link LRAParticipant} class or null if no non-JAX-RS methods have been found
     */
    private LRAParticipant getAsParticipant(ClassInfo classInfo) throws ClassNotFoundException {
        Class<?> javaClass = getClass().getClassLoader().loadClass(classInfo.name().toString());

        if (javaClass.isInterface() || Modifier.isAbstract(javaClass.getModifiers()) || !isLRAParticipant(classInfo)) {
            return null;
        }

        LRAParticipant participant = new LRAParticipant(javaClass);
        return participant.hasNonJaxRsMethods() ? participant : null;
    }

    /**
     * Returns whether the classinfo represents an LRA participant --
     * Class contains LRA method and either one or both of Compensate and/or AfterLRA methods.
     *
     * @param classInfo Jandex class object to scan for annotations
     *
     * @return true if the class is a valid LRA participant, false otherwise
     * @throws IllegalStateException if there is LRA annotation but no Compensate or AfterLRA is found
     */
    private boolean isLRAParticipant(ClassInfo classInfo) {
        Map<DotName, List<AnnotationInstance>> annotations = JandexAnnotationResolver.getAllAnnotationsFromClassInfoHierarchy(classInfo.name(), index);

        if (!annotations.containsKey(DotNames.LRA)) {
            return false;
        } else if (!annotations.containsKey(DotNames.COMPENSATE) && !annotations.containsKey(DotNames.AFTER_LRA)) {
            throw new IllegalStateException(String.format("%s: %s",
                classInfo.name(), "The class contains an LRA method and no Compensate or AfterLRA method was found."));
        } else {
            return true;
        }
    }
}
