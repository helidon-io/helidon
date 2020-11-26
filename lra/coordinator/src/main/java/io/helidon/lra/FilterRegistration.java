package io.helidon.lra;


import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Provider
public class FilterRegistration implements DynamicFeature {
    private boolean isRegistered;

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext ctx) {
        if (!isRegistered) {
            Method method = resourceInfo.getResourceMethod();
            Annotation transactional = method.getDeclaredAnnotation(LRA.class);

            if (transactional != null || method.getDeclaringClass().getDeclaredAnnotation(LRA.class) != null) {
//                ctx.register(ServerLRAFilter.class);
                isRegistered = true;
            }
        }
    }
}