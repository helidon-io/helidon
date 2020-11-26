package io.helidon.lra;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;


@ApplicationScoped
@ApplicationPath("/")
public class CoordinatorApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<>();
        s.add(Coordinator.class);
        s.add(RecoveryManager.class);
        s.add(FilterRegistration.class);
//        s.add(MessageProcessing.class);
//        s.add(SendingResource.class);
//        s.add(RecoveryService.class);
        return s;
    }

}

