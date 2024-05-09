# Helidon Examples

The Helidon examples have moved out of the main Helidon repository (`helidon`) into
the Helidon examples repository (`helidon-examples`). 

This creates a potential chicken-and-egg issue with the examples validation check that is run on PRs.
This document describes how we handle that situation.

## Example Validation Check

The primary Helidon repository contains a validation check for examples. This check
pulls the development branch for the corresponding major version from `helidon-examples`
(for example `dev-3.x`) and builds it to verify the PR has not broken examples.

This check *is not required*, because there are times when a fix in the primary repository
might legitimately break an example. Therefore if this check fails it will not block
merging of the PR.

Of course breaking examples is a red flag that you might be introducing an incompatibility. So
make sure you fully understand why the example broke and if you are introducing an incompatibility.

## Workflow

Let's assume you need to update the primary repository and an example because your change to the primary
repository breaks an example. You wish you could do this at the same time in one PR -- but you can't.
Here is the workflow:

1. Create the PR against the primary repository (`helidon`) with the "breaking" change. The example validation will fail.
2. Create a PR against the example repository (`helidon-examples`) with the fix for the example. The build check might fail.
3. Add a comment to the first PR describing why the example check fails, why this isn't a compatiblity issue, and include a link to the second PR (that fixes the example).
4. Have the first PR reviewed and merge it (the example check will not block merging)
5. Re-run the build validation on the second PR. It will now pass
6. Have the second PR reviewed and merge it.

All done!

