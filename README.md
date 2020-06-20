# MoccaFaux

*Adapt power management to changes in the environment.*

MoccaFaux can be used to prevent the activation of screen savers and
power-saving modes when certain conditions are met.  It was inspired
by [caffeine], but is much more flexible.

In fact, MoccaFaux is a scheduler that executes commands, records
their exit code and executes other commands depending on these exit
codes:

1. execute commands and record their exit code (the commands as well
   as their number are user-defined)

1. assign exit codes to user-defined tasks (such as `:control-dpms`,
   again user-defined)

1. for each task, check whether any of the executed commands had a
   **zero exit code**

1. in case the result of a task differs from the last run, execute a
   command depending on the result (you probably guessed it, commands
   are user-defined)

MoccaFaux is so flexible that it could be used for other tasks, for
example compiling an application if files in a directory change.  In
practice, you might be better advised to use dedicated software and
APIs like [Gulp.watch] and [inotify].

## Installation

You need an installation of Java SE 11 (any version for SE 8 probably
works just fine).

If `git` and `lein` are installed on your system, then run this:

```bash
git clone https://github.com/mzuther/moccafaux.git
cd moccafaux
lein clean && lein uberjar
```

Or simply download a pre-compiled JAR file from the [releases].

## Usage

Open a shell and locate to the MoccaFaux directory.  Then execute:

```bash
# for MALLOC_ARENA_MAX, see https://issues.apache.org/jira/browse/HADOOP-7154)
MALLOC_ARENA_MAX=4 java -jar ./target/uberjar/moccafaux.jar
```

## License

Copyright (c) 2020 [Martin Zuther]

This program and the accompanying materials are made available under
the terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following
Secondary Licenses when the conditions for such availability set forth
in the Eclipse Public License, v. 2.0 are satisfied: GNU General
Public License as published by the Free Software Foundation, either
version 2 of the License, or (at your option) any later version, with
the GNU Classpath Exception which is available at
https://www.gnu.org/software/classpath/license.html.

Thank you for using free software!


[caffeine]:       https://launchpad.net/caffeine
[inotify]:        https://en.wikipedia.org/wiki/Inotify
[Gulp.watch]:     https://gulpjs.com/docs/en/getting-started/watching-files

[Martin Zuther]:  http://www.mzuther.de/
[releases]:       https://github.com/mzuther/moccafaux/releases
