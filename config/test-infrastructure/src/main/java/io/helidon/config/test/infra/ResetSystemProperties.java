package io.helidon.config.test.infra;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation used to reset system properties to the state before test execution.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtendWith(RestoreSystemPropertiesExt.class)
public @interface ResetSystemProperties {
}
