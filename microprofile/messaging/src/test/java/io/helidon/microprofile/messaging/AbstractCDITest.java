package io.helidon.microprofile.messaging;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.messaging.kafka.connector.KafkaConnectorFactory;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.config.MpConfigProviderResolver;
import io.helidon.microprofile.server.Server;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.LogManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractCDITest {

    static {
        try (InputStream is = KafkaCdiExtensionTest.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            fail(e);
        }
    }

    protected static final Connector KAFKA_CONNECTOR_LITERAL = new Connector() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return Connector.class;
        }

        @Override
        public String value() {
            return KafkaConnectorFactory.CONNECTOR_NAME;
        }
    };

    private SeContainer cdiContainer;

    protected void cdiConfig(Properties p){
        //Default config
    }

    abstract void cdiBeanClasses(Set<Class<?>> classes);

    @BeforeEach
    public void setUp() {
        Properties p = new Properties();
        Set<Class<?>> classes = new HashSet<>();
        cdiBeanClasses(classes);
        cdiConfig(p);
        cdiContainer = startCdiContainer(p, classes);
    }

    @AfterEach
    public void tearDown() {
        if (cdiContainer != null) {
            cdiContainer.close();
        }
    }

    protected <T> void forEachBean(Class<T> beanType, Annotation annotation, Consumer<T> consumer){
        cdiContainer.select(beanType, annotation).stream().forEach(consumer);
    }

    public static SeContainer startCdiContainer(Properties p, Set<Class<?>> beanClasses) {
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        final Server.Builder builder = Server.builder();
        assertNotNull(builder);
        builder.config(config);
        MpConfigProviderResolver.instance()
                .registerConfig(MpConfig.builder()
                                .config(config).build(),
                        Thread.currentThread().getContextClassLoader());
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, is(notNullValue()));
        initializer.addBeanClasses(beanClasses.toArray(new Class<?>[0]));
        return initializer.initialize();
    }
}
