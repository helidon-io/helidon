# helidon-load-testing
Load testing for Helidon

# REST API

|Verb|Path|Description
|----|----|-----------|
|GET|/books|Returns list of books|
|POST|/books|Adds a new book|
|GET|/books/{isbn}|Returns book with the given isbn number|
|PUT|/books/{isbn}|Updates book with the given isbn number|
|DELETE|/books/{isbn}|Deletes book with the given isbn number|

# Sample JSON

See `helidon-mp/src/test/book.json`

# Example Curl Commands

```bash
curl -H "Content-Type: application/json" \
 -X POST http://localhost:8080/books \
 --data @helidon-mp/target/test-classes/book.json 
 
curl -H 'Accept: application/json' -X GET http://localhost:8080/books

curl -H 'Accept: application/json' -X GET http://localhost:8080/books/123456

curl -H "Content-Type: application/json" \
 -X PUT http://localhost:8080/books/1234 \
 --data @helidon-mp/target/test-classes/book.json 
 
curl -X DELETE http://localhost:8080/books/123456
```

# Local Shell Setup

1. Ensure you have jmeter 3.3 installed. You can get it from 
https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-3.3.zip.
2. Ensure that you have exported a `JMETER_HOME` env var pointing to the unzipped directory from #1.
2. `source bin/perf.profile`

There are now a number of convenience functions setup in your shell which we'll use below. It'd probably be a good idea to
look at `perf.profile` and get an idea of what it does/provides.

In addition to supporting local testing, the profile has built-in knowledge of two remote hosts for running perf tests, one dual 
core (8 GB) and one quad core (16 GB)...


# Remote Shell Setup

From a local shell that has sourced perf.profile:

1. Run the `initRemote` command. This first tries to ensure that ssh does not require a password, then creates a "helidon" 
directory in your home dir and copies required files to it (perf.profile, *.jmx). If you prefer a different directory, you must 
export HELIDON_PERF_HOME in both your local and remote bash profiles. This command can be run safely anytime one of the required
files is changed.
2. If you don't have jmeter installed in your remote dir, use the `cphome` command to copy the zip (see #1 in local shell setup), 
e.g.:
    ```bash
    cphome apache-jmeter-3.3.zip 
    ```
    and then, connect to a remote machine using `dual` and unzip that file into your home directory.   

3. Connect using `dual` (or `quad`) and add the following to your bash profile (e.g. `.bashrc`):
   ```bash
   export JMETER_HOME=~/apache-jmeter-3.3 # or wherever you put it 
   [[ -e ~/helidon/perf.profile ]] && source ~/helidon/perf.profile # or use ${HELIDON_PERF_HOME} instead of ~/helidon
   ```
 
# Copying Binaries

1. Create distribution zips using the `dist <name>` command, e.g. `dist baseline`. Note the file names that this creates, you'll
use them wherever a `<distName>` is required.
2. Copy and unpack the desired distribution file(s) to your remote home dir using the `cpdist <file>` command. (This uses `cphome` then runs
the `unpack` command on the remote machine.)

# Running Remotely

You'll want 4 different terminals, two for each remote machine. On each machine we're going to monitor cpu usage in one terminal
while we run either the server or jmeter in the other.

Generally we should use `dual` for the server and `quad` for jmeter; the instructions below make that assumption.

Connect two terminals using `dual`, and the other two using `quad`. 

1. If required, run `cdh` in any terminal that is not in the `helidon` directory.
1. On `dual`: start the server using `server <distName` in one terminal, and cpu monitoring using `cpu` in the other. 
1. On `quad`: start jmeter using `load <distName` in one terminal, and cpu monitoring using `cpu` in the other. 

Once jmeter has run for 5 minutes, Ctrl-C in all 4 terminals.

Note that the `cpu` command will print an average CPU % once killed. 
