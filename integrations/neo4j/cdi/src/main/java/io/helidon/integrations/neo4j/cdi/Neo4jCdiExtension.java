package io.helidon.integrations.neo4j.cdi;

import java.util.logging.Level;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.util.AnnotationLiteral;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;

import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Created by Dmitry Alexandrov on 12.11.20.
 */
public class Neo4jCdiExtension implements Extension {

    private static org.neo4j.driver.Config.ConfigBuilder createBaseConfig() {
        org.neo4j.driver.Config.ConfigBuilder configBuilder = org.neo4j.driver.Config.builder();
        Logging logging;
        try {
            logging = Logging.slf4j();
        } catch (Exception e) {
            logging = Logging.javaUtilLogging(Level.INFO);
        }
        configBuilder.withLogging(logging);
        return configBuilder;
    }

    private static void configureSsl(org.neo4j.driver.Config.ConfigBuilder configBuilder,
                                     Neo4jSupport neo4JSupport) {

        if (neo4JSupport.encrypted) {
            configBuilder.withEncryption();
            configBuilder.withTrustStrategy(neo4JSupport.toInternalRepresentation());
        } else {
            configBuilder.withoutEncryption();
        }
    }

    private static void configurePoolSettings(org.neo4j.driver.Config.ConfigBuilder configBuilder, Neo4jSupport neo4JSupport) {

        configBuilder.withMaxConnectionPoolSize(neo4JSupport.maxConnectionPoolSize);
        configBuilder.withConnectionLivenessCheckTimeout(neo4JSupport.idleTimeBeforeConnectionTest.toMillis(), MILLISECONDS);
        configBuilder.withMaxConnectionLifetime(neo4JSupport.maxConnectionLifetime.toMillis(), MILLISECONDS);
        configBuilder.withConnectionAcquisitionTimeout(neo4JSupport.connectionAcquisitionTimeout.toMillis(), MILLISECONDS);

        if (neo4JSupport.metricsEnabled) {
            configBuilder.withDriverMetrics();
        } else {
            configBuilder.withoutDriverMetrics();
        }
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery addEvent, BeanManager beanManager) {
        final org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        final Config helidonConfig = MpConfig.toHelidonConfig(config).get("neo4j");

        addEvent.addBean()
                .types(Driver.class)
                .qualifiers(new AnnotationLiteral<Default>() {
                }, new AnnotationLiteral<Any>() {
                })
                .scope(ApplicationScoped.class)
                .name(Driver.class.getName())
                .beanClass(Driver.class)
                .createWith(creationContext -> {
                    ConfigValue<Neo4jSupport> configValue = helidonConfig.as(Neo4jSupport::create);
                    Neo4jSupport neo4JSupport = configValue.get();

                    String uri = neo4JSupport.uri;
                    AuthToken authToken = AuthTokens.none();
                    if (!neo4JSupport.disabled) {
                        authToken = AuthTokens.basic(neo4JSupport.username, neo4JSupport.password);
                    }

                    org.neo4j.driver.Config.ConfigBuilder configBuilder = createBaseConfig();
                    configureSsl(configBuilder, neo4JSupport);
                    configurePoolSettings(configBuilder, neo4JSupport);

                    Driver driver = GraphDatabase.driver(uri, authToken, configBuilder.build());

                    return driver;
                });
    }

}
