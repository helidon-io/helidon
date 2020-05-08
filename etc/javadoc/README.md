# Offline Javadoc package-list and element-list files for external links

This directory contains `package-list` and `element-list` files for javadocs.
Previously we simply used the `<link>` option to resolve external javadoc links.
But as our number of links grew building javadocs got slow. And since this 
happens for (most) every module, it really slowed down the build.

To address this we turned to using `<offlineLinks>`. With offline links you
need to have a copy of the linked-to javadoc's `package-list` (or `element-list`)
locally. That's a hassle. But because it is local it doesn't need to be 
fetched (repeatedly) at build time. And as a result building javadocs is faster.

To help maintain this local cache of `package-list` files there is a script:

`etc/scripts/gen-javadoc-packagelist.sh`

When this script is run in (crudly) parses the project `pom.xml`, extracts
some properties, and fetches the `package-list` (`element-list`) files and
squirrels them away in this directory. They can then be checked into the
workspace and referenced in the project `pom.xml`.

# How to use this stuff

OK, so if you need to add a new link to external javadocs this is what you do

1. Add a new property to the root `pom.xml` with the link to the exernal javadocs. The name
   must folow the naming convention shown here (`javadoc.link` as a prefix with a short
   name following). The value is the same as what you would use in a `<link>`. For example:
   ```
        <javadoc.link.jaxrs>https://jax-rs.github.io/apidocs/${version.lib.jaxrs-api}</javadoc.link.jaxrs>
   ```
   
2. Add a new `<offlineLink>` to the javadoc plugin configuration. It should reference the property
   you just created in step 1 for the link `<url>`. The `<location>` is this directory
   (using a convenient property) followed by the short name you used after the prefix for
   the property defined in step #1. Following this naming convention is important! For example:
   ```
                            <offlineLink>
                                <url>${javadoc.link.jaxrs}</url>
                                <location>${javadoc.packagelist.dir}/jaxrs</location>
                            </offlineLink>
   ```
   
3. Now that the pom is configured you need to run the script to generate the `package-list`
   (`element-list`) file for you. Note that this will re-generate all of them, but that's OK.
   The other ones should not change. Plus you can re-checkout the originals if something 
   goes wrong. Anyway, just run the script like:
   ```
   gen-javadoc-packagelist.sh
   ```
   This will generate output as it makes progress. If anything goes wrong (like it can't
   download a `package-list` (`element-list`) file it will complain. You'll then need 
   to figure out why the URL is no good. But better now, rather than during a javadoc build.
   
4. If all goes well you should have a `package-list` (`element-list`) file in
   this directory under the component's name. You can now run a javadoc build and verify
   all is good.
   
5. If all is good make sure to `git add` your new `package-list` (`element-list`) file
   and commit it



