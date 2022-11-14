# default-tools

This module is responsible for providing the basic tooling around code generation and dependency model analysis during compile-time. All services are standard <i>ServiceLocator</i> based, and all use a standard request/response mechanism in the API. See the javadoc for specific details for each.

### io.helidon.pico.tools.creator.ApplicationCreator
This tool creates the picoApplication.

### io.helidon.pico.tools.creator.ActivatorCreator
This tool creates the picoActivator(s) and picoModule(s).

### io.helidon.pico.tools.creator.ExternalModuleCreator
This tool creates the request to analyze a 3rd party jar, and then generates the request payload appropriate to hand off to the ActivatorCreator tool to generate the code.

### io.helidon.pico.tools.creator.InterceptorCreator
Called when an <i>InterceptorTrigger</i> is found on a service that requires interception.

## Mustache / Handlebar Based CodeGen
All templates used are found under [src/main/resources/templates](./src/main/resources/templates).
