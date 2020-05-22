/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.core;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

/**
 * An extension that processes beans as they are discovered.
 */
public class GrpcCdiExtension implements Extension {

    /**
     * Determine whether a discovered bean has a superclass or implements an
     * interface that is annotated with {@link Grpc} and if so then also
     * annotate the bean with the same annotation.
     * <p>
     * This is required so that we can support the use-case where an interface has been
     * annotated with {@link Grpc} but the implementation class has not but the
     * implementation class is annotated with a bean discovering annotation such as
     * {@link javax.enterprise.context.ApplicationScoped}. We need to make sure that the
     * gRPC server can locate beans so we add the {@link Grpc} from the interface to
     * the bean.
     *
     * @param event the {@link ProcessAnnotatedType} event
     */
    public void beforeBean(@Observes @WithAnnotations(Grpc.class) ProcessAnnotatedType<?> event) {
        AnnotatedType<?> type = event.getAnnotatedType();
        Class<?> javaClass = type.getJavaClass();
        Class<?> annotatedClass = ModelHelper.getAnnotatedResourceClass(javaClass, Grpc.class);
        if (annotatedClass != javaClass && annotatedClass.isAnnotationPresent(Grpc.class)) {
            event.configureAnnotatedType().add(annotatedClass.getAnnotation(Grpc.class));
        }
    }
}
