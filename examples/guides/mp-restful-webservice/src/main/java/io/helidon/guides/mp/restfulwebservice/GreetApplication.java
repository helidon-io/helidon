/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 */
package io.helidon.guides.mp.restfulwebservice;

// tag::javaImports[]
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
// end::javaImports[]

// tag::helidonImports[]
import io.helidon.common.CollectionsHelper;
// end::helidonImports[]
/**
 * JAX-RS application class.
 */
// tag::greetAppBody[]
@ApplicationScoped // <1>
@ApplicationPath("/") // <2>
public class GreetApplication extends Application { // <3>

    @Override
    public Set<Class<?>> getClasses() { // <4>
        return CollectionsHelper.setOf(GreetResource.class);
    }
}
// end::greetAppBody[]
