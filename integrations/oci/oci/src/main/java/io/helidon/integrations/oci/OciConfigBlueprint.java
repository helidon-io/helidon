package io.helidon.integrations.oci;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Configured("oci")
@Prototype.Blueprint
interface OciConfigBlueprint {
    String STRATEGY_AUTO = "auto";
    String IMDS_ADDRESS = "169.254.169.254";

    /**
     * Authentication strategy to use. If the configured strategy is not available, an exception
     * would be thrown for OCI related services.
     * <p>
     * Known and supported authentication strategies for public OCI:
     * <ul>
     *     <li>{@value #STRATEGY_AUTO} - use the list of {@link #allowedAtnStrategies()} (in the provided order), and choose
     *     the first one
     *     capable of providing data</li>
     *     <li>{@value AtnStrategyConfig#STRATEGY} -
     *     use configuration of the application to obtain values needed to set up connectivity, uses
     *     {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider}</li>
     *     <li>{@value AtnStrategyConfigFile#STRATEGY} - use configuration file of OCI ({@code home/.oci/config}), uses
     *     {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}</li>
     *     <li>{@value AtnStrategyResourcePrincipal#STRATEGY}  - use identity of the OCI resource the service is executed on (fn), uses
     *     {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}</li>
     *     <li>{@value AtnStrategyInstancePrincipal#STRATEGY} - use identity of the OCI instance the service is running on, uses
     *     {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}</li>
     * </ul>
     *
     * @return the authentication strategy to apply
     */
    @Option.Configured
    @Option.Default("auto")
    String atnStrategy();

    /**
     * List of attempted authentication strategies in case {@link #atnStrategy()} is set to {@value #STRATEGY_AUTO}.
     * <p>
     * In case the list is empty, all available strategies will be tried, ordered by their {@link io.helidon.common.Weight}
     *
     * @return list of authentication strategies to be tried
     * @see #atnStrategy()
     */
    @Option.Configured
    List<String> allowedAtnStrategies();

    /**
     * Config strategy configuration (if provided and used).
     *
     * @return information needed for config {@link #atnStrategy()}
     */
    @Option.Configured("config-strategy")
    Optional<ConfigStrategyConfigBlueprint> configStrategyConfig();

    /**
     * Config file strategy configuration (if provided and used).
     *
     * @return information to customize config for {@link #atnStrategy()}
     */
    @Option.Configured("config-file-strategy")
    Optional<ConfigFileStrategyConfigBlueprint> configFileStrategyConfig();

    /**
     * The OCI IMDS address or hostname.
     * <p>
     * This configuration property is used to identify the metadata service url.
     *
     * @return the OCI IMDS hostname
     */
    @Option.Configured
    @Option.Default(IMDS_ADDRESS)
    String imdsAddress();

    /**
     * The OCI IMDS connection timeout. This is used to auto-detect availability.
     * <p>
     * This configuration property is used when attempting to connect to the metadata service.
     *
     * @return the OCI IMDS connection timeout
     */
    @Option.Configured
    @Option.Default("PT0.1S")
    Duration imdsTimeout();
}
