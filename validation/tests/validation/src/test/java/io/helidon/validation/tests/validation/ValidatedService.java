package io.helidon.validation.tests.validation;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.validation.Constraints;
import io.helidon.validation.Validation;

@Service.Singleton
class ValidatedService {
    @Service.Inject
    @Constraints.NotNull
    Config config;

    @Constraints.String.NotBlank
    String process(@Validation.Valid @Constraints.NotNull ValidatedType type) {
        if (type.second() == 42) {
            return "Good";
        }
        if (type.second() == 43) {
            return "";
        }
        return "Bad";
    }

    void process(List<@Validation.Valid ValidatedType> list) {
    }
}
