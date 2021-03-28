package io.helidon.examples.lra;

import io.helidon.lra.rest.*;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;


@ApplicationScoped
@ApplicationPath("/inventory")
public class RestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<>();
        s.add(ClientLRARequestFilter.class);
        s.add(ClientLRAResponseFilter.class);
        s.add(ServerLRAFilter.class);
        s.add(RestInventoryResource.class);
        return s;
    }

}
