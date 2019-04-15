# Helidon Archetypes

These are scripts for creating and deploying the Helidon quickstart archetypes.
To simplify maintenance of the quickstart examples and archetypes we generate
 the archetypes on the fly using a shell script.

We also want to keep the example poms clean, which further limits our option
 (don't want any archetype configuration in the example poms).

So, we resorted to the following scripts:
* `create-archetype.sh`: Creates a single archetype from an existing project
* `create-archetypes.sh`: Creates all the Helidon archetypes
* `set-version.sh`: Iterates over the quickstart examples poms and changes the
  `helidon.version` property in the poms to be the specified version.
* `deploy-archetypes.sh`: calls create-archetypes.sh to create and build the
   archetypes, and then deploys them.

## Making changes to quickstart examples and trying the archetypes

For local development you can change the examples at will
and try them out. Once you are done messing with the examples
and you want to try out the archetypes, do this:

```
bash test-archetypes.sh
```
