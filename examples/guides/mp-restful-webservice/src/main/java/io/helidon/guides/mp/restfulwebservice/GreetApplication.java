/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 */
package io.helidon.guides.mp.restfulwebservice;

import io.helidon.common.CollectionsHelper;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 *
 * @author tjquinn
 */
@ApplicationScoped
@ApplicationPath("/")
public class GreetApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return CollectionsHelper.setOf(GreetResource.class);
    }

}
