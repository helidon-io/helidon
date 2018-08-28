ABAC (Attribute based access control) support
---

ABAC primary support project is the [ABAC security provider](../security-providers/abac-provider). It defines
SPI to plug-in validators for various attributes.

The following table lists validators currently available:

| Validator | Configuration Key | Annotations | Description |
| --------- | ----------------- | ----------- | ----------- |
| [time](time-validator/src/main/java/io/helidon/security/abac/time/TimeValidator.java)      | time-validator    | DaysOfWeek, TimesOfDay | Supports time of day and day of week checks |
| [role](role-validator/src/main/java/io/helidon/security/abac/role/RoleValidator.java)      | role-validator    | RolesAllowed, Roles | Checks whether user/service is in role(s) |
| [scope](scope-validator/src/main/java/io/helidon/security/abac/scope/ScopeValidator.java)     | scope-validator   | Scope       | Checks whether user has the defined scopes | 
| [policy](policy-validator/src/main/java/io/helidon/security/abac/policy/PolicyValidator.java)    | policy-validator  | PolicyStatement | Evaluates policy statement(s) using a policy executor (SPI) |
| [policy-el](policy-el/src/main/java/io/helidon/security/abac/policy/el/JavaxElPolicyExecutor.java) | policy-javax-el   | PolicyStatement | Policy executor using Java EE policy expression language (EL) |
