/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.jersey.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link InvokedResourceImpl}.
 */
class InvokedResourceImplTest {
    private static Method anInterfaceMethod;
    private static Method topLevelMethod;
    private static Method topLevelImplementingNoAnnot;
    private static Method secondMethod;
    private static Method secondImplementingMethod;
    private static Method secondImplementingNoAnnotMethod;

    @BeforeAll
    static void initClass() throws NoSuchMethodException {
        anInterfaceMethod = AnInterface.class.getMethod("aMethod");
        topLevelMethod = TopLevel.class.getMethod("aMethod");
        topLevelImplementingNoAnnot = TopLevelImplementingNoAnnot.class.getMethod("aMethod");
        secondMethod = Second.class.getMethod("aMethod");
        secondImplementingMethod = SecondImplement.class.getMethod("aMethod");
        secondImplementingNoAnnotMethod = SecondImplementNoAnnot.class.getMethod("aMethod");
    }

    @Test
    void testTopLevel() {
        Class<?> handlingClass = TopLevel.class;

        Resource.Builder resourceBuilder = Resource.builder(TopLevel.class);

        // final class
        ResourceMethod method = resourceBuilder.addMethod()
                .handledBy(handlingClass, topLevelMethod)
                .build();

        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        when(uriInfo.getMatchedResourceMethod()).thenReturn(method);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        InvokedResource invokedResource = InvokedResourceImpl.create(requestContext);

        assertThat(invokedResource.definitionMethod(), is(Optional.of(topLevelMethod)));
        assertThat(invokedResource.handlingMethod(), is(Optional.of(topLevelMethod)));
        assertThat(invokedResource.definitionClass(), is(Optional.of(TopLevel.class)));
        assertThat(invokedResource.handlingClass(), is(Optional.of(TopLevel.class)));
        assertThat(invokedResource.findAnnotation(Path.class), is(Optional.of(path("TopLevel.aMethod"))));
        assertThat(invokedResource.findMethodAnnotation(Path.class), is(Optional.of(path("TopLevel.aMethod"))));
        assertThat(invokedResource.findClassAnnotation(Path.class), is(Optional.of(path("TopLevel"))));
        assertThat(invokedResource.findClassAnnotation(RolesAllowed.class), is(Optional.empty()));
    }

    @Test
    void testTopLevelImplementingNoAnnot() {
        Class<?> handlingClass = TopLevelImplementingNoAnnot.class;

        Resource.Builder resourceBuilder = Resource.builder(AnInterface.class);

        // final class
        ResourceMethod method = resourceBuilder.addMethod()
                .handledBy(handlingClass, anInterfaceMethod)
                .build();

        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        when(uriInfo.getMatchedResourceMethod()).thenReturn(method);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        InvokedResource invokedResource = InvokedResourceImpl.create(requestContext);

        assertThat(invokedResource.definitionMethod(), is(Optional.of(anInterfaceMethod)));
        assertThat(invokedResource.handlingMethod(), is(Optional.of(topLevelImplementingNoAnnot)));
        assertThat(invokedResource.definitionClass(), is(Optional.of(AnInterface.class)));
        assertThat(invokedResource.handlingClass(), is(Optional.of(TopLevelImplementingNoAnnot.class)));
        assertThat(invokedResource.findAnnotation(Path.class), is(Optional.of(path("AnInterface.aMethod"))));
        assertThat(invokedResource.findMethodAnnotation(Path.class), is(Optional.of(path("AnInterface.aMethod"))));
        assertThat(invokedResource.findClassAnnotation(Path.class), is(Optional.of(path("AnInterface"))));
        assertThat(invokedResource.findClassAnnotation(RolesAllowed.class), is(Optional.empty()));
    }

    @Test
    void testSecond() {
        Class<?> handlingClass = Second.class;
        Method handlingMethod = secondMethod;
        Class<?> definitionClass = TopLevel.class;
        Method definitionMethod = secondMethod;

        Resource.Builder resourceBuilder = Resource.builder(definitionClass);

        // final class
        ResourceMethod method = resourceBuilder.addMethod()
                .handledBy(handlingClass, handlingMethod)
                .build();

        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        when(uriInfo.getMatchedResourceMethod()).thenReturn(method);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        InvokedResource invokedResource = InvokedResourceImpl.create(requestContext);

        assertThat(invokedResource.definitionMethod(), is(Optional.of(definitionMethod)));
        assertThat(invokedResource.handlingMethod(), is(Optional.of(handlingMethod)));
        assertThat(invokedResource.definitionClass(), is(Optional.of(definitionClass)));
        assertThat(invokedResource.handlingClass(), is(Optional.of(handlingClass)));
        assertThat(invokedResource.findAnnotation(Path.class), is(Optional.of(path("Second.aMethod"))));
        assertThat(invokedResource.findMethodAnnotation(Path.class), is(Optional.of(path("Second.aMethod"))));
        assertThat(invokedResource.findClassAnnotation(Path.class), is(Optional.of(path("TopLevel"))));
        assertThat(invokedResource.findClassAnnotation(RolesAllowed.class), is(Optional.empty()));
    }

    @Test
    void testSecondImplement() {
        Class<?> handlingClass = SecondImplement.class;
        Method handlingMethod = secondImplementingMethod;
        Class<?> definitionClass = AnInterface.class;
        Method definitionMethod = secondImplementingMethod;

        Resource.Builder resourceBuilder = Resource.builder(definitionClass);

        // final class
        ResourceMethod method = resourceBuilder.addMethod()
                .handledBy(handlingClass, handlingMethod)
                .build();

        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        when(uriInfo.getMatchedResourceMethod()).thenReturn(method);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        InvokedResource invokedResource = InvokedResourceImpl.create(requestContext);

        assertThat(invokedResource.definitionMethod(), is(Optional.of(definitionMethod)));
        assertThat(invokedResource.handlingMethod(), is(Optional.of(handlingMethod)));
        assertThat(invokedResource.definitionClass(), is(Optional.of(definitionClass)));
        assertThat(invokedResource.handlingClass(), is(Optional.of(handlingClass)));
        assertThat(invokedResource.findAnnotation(Path.class), is(Optional.of(path("SecondImplement.aMethod"))));
        assertThat(invokedResource.findMethodAnnotation(Path.class), is(Optional.of(path("SecondImplement.aMethod"))));
        assertThat(invokedResource.findClassAnnotation(Path.class), is(Optional.of(path("AnInterface"))));
        assertThat(invokedResource.findClassAnnotation(RolesAllowed.class), is(Optional.empty()));
    }

    @Test
    void testSecondImplementNoAnnot() {
        Class<?> handlingClass = SecondImplementNoAnnot.class;
        Method handlingMethod = secondImplementingNoAnnotMethod;
        Class<?> definitionClass = AnInterface.class;
        Method definitionMethod = topLevelImplementingNoAnnot;

        Resource.Builder resourceBuilder = Resource.builder(definitionClass);

        // final class
        ResourceMethod method = resourceBuilder.addMethod()
                .handledBy(handlingClass, handlingMethod)
                .build();

        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        when(uriInfo.getMatchedResourceMethod()).thenReturn(method);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        InvokedResource invokedResource = InvokedResourceImpl.create(requestContext);

        assertThat(invokedResource.definitionMethod(), is(Optional.of(definitionMethod)));
        assertThat(invokedResource.handlingMethod(), is(Optional.of(handlingMethod)));
        assertThat(invokedResource.definitionClass(), is(Optional.of(definitionClass)));
        assertThat(invokedResource.handlingClass(), is(Optional.of(handlingClass)));
        assertThat(invokedResource.findAnnotation(Path.class), is(Optional.of(path("AnInterface.aMethod"))));
        assertThat(invokedResource.findMethodAnnotation(Path.class), is(Optional.of(path("AnInterface.aMethod"))));
        assertThat(invokedResource.findClassAnnotation(Path.class), is(Optional.of(path("AnInterface"))));
        assertThat(invokedResource.findClassAnnotation(RolesAllowed.class), is(Optional.empty()));
    }


    private Path path(String path) {
        return new Path() {
            @Override
            public String value() {
                return path;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Path.class;
            }

            @Override
            public String toString() {
                return path;
            }
        };
    }

    @Path("AnInterface")
    private interface AnInterface {
        @Path("AnInterface.aMethod")
        @RolesAllowed("AnInterface.aMethod")
        String aMethod();
    }

    @Path("TopLevel")
    private static class TopLevel {
        @Path("TopLevel.aMethod")
        public String aMethod() {
            return "TopLevel.aMethod";
        }
    }

    @Path("TopLevelClassAnnot")
    private static class TopLevelClassAnnot {
        public String aMethod() {
            return "TopLevel.aMethod";
        }
    }

    private static class TopLevelImplementing implements AnInterface {
        @Path("TopLevelImplementing.aMethod")
        @Override
        public String aMethod() {
            return "TopLevel.aMethod";
        }
    }

    private static class TopLevelImplementingNoAnnot implements AnInterface {
        @Override
        public String aMethod() {
            return "TopLevel.aMethod";
        }
    }

    private static class Second extends TopLevel {
        @Override
        @Path("Second.aMethod")
        public String aMethod() {
            return "Second.aMethod";
        }
    }

    private static class SecondImplement extends TopLevelImplementingNoAnnot {
        @Override
        @Path("SecondImplement.aMethod")
        @RolesAllowed("test")
        public String aMethod() {
            return "SecondImplement.aMethod";
        }
    }

    private static class SecondImplementNoAnnot extends TopLevelImplementingNoAnnot {

    }
}