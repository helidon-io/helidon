package io.helidon.common;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Version}.
 */
class VersionTest {
    @Test
    void testVersion() {
        Version v = new Version();
        System.out.println("Version: " + v.toString());
        assertThat(Version.VERSION, not("unknown"));
        assertThat(Version.BUILD_TIMESTAMP, not("unknown"));
    }
}
