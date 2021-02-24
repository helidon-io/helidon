package io.helidon.examples.lra;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
@ApplicationPath("/")
public class MessagingApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<>();
        s.add(AQMessagingResource.class);
        s.add(KafkaMessagingResource.class);
        s.add(ATPAQAdminResource.class);
        return s;
    }
}