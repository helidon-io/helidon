package io.helidon.metadata.compile;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for Spotbugs annotations.
 * <p>
 * Use {@link io.helidon.metadata.compile.Spotbugs.Exclude} to add an exclusion to spotbugs execution.
 * In case the exclusion cannot be done through annotation (due to something that is unsupported), you can
 * still create a custom exclude.xml.
 */
public final class Spotbugs {
    private Spotbugs() {
    }

    @Target({ElementType.TYPE,
            ElementType.METHOD,
            ElementType.ANNOTATION_TYPE,
            ElementType.FIELD,
            ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Exclude {
        /**
         * The bug patterns to exclude.
         *
         * @return pattern(s) to exclude
         */
        String[] pattern();

        /**
         * Reason for the exclusion. This should clearly explain why this is not a problem.
         *
         * @return reason we are excluding this pattern
         */
        String reason();
    }
}
