package io.helidon.examples.lra;

import io.narayana.lra.filter.*;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;


@ApplicationScoped
@ApplicationPath("/")
public class RestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<>();
        s.add(io.narayana.lra.filter.ClientLRARequestFilter.class);
        s.add(io.narayana.lra.filter.ClientLRAResponseFilter.class);
        s.add(io.narayana.lra.filter.ServerLRAFilter.class);
        s.add(RestResource.class);
        return s;
    }

}
