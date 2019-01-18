Attribute Based Access Control (ABAC) Security Provider
---

This provider is an authorization provider validating various attributes against
configured validators.

Any attribute of the following objects can be used:
 - environment (such as time of request) - e.g. env.time.year
 - subject (user) - e.g. subject.principal.id
 - subject (service) - e.g. service.principal.id
 - object (must be explicitly invoked by developer in code, as object cannot be automatically added to security context) - e.g. object.owner
 
This provider checks that all defined ABAC validators are validated.
If there is a definition for a validator (e.g. an annotation) that is not checked,
the request is denied.

See [available validators](../../abac-validators).