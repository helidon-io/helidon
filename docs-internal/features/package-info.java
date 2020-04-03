@Feature(value = "Security/Providers/ABAC/Validators/EL",
        description = "Expression language validator for ABAC security provider.");
@HelidonFlavor("SE", "MP")
package io.helidon.security.abac.el {

}

@Features(
    @Feature(value = "Security/Providers/Digest-Auth",
        description = "Security provider for HTTP Digest Authentication").
    @Feature(value = "Security/Providers/Basic-Auth",
        description = "Security provider for HTTP Basic authentication and identity propagation")
)
@HelidonFlavor("SE", "MP")
package io.helidon.security.providers.httpauth {

}

