Server
----

# Testing

Most tests are located under `nima/tests/integration/webserver/webserver`
as it is much easier to use the JUnit extension that requires module which depends on server, which would create a circular
dependency if used from here.