# MoccaFaux

*Adapt power management to changes in the environment.*

MoccaFaux can be used to prevent the activation of screen savers and
power-saving modes when certain conditions are met.  It was inspired
by [caffeine], but is much more flexible.

In fact, MoccaFaux is a scheduler that executes commands, looks at
their exit codes and then decides whether to execute different
commands:

1. execute commands (called *watches*) and record their exit code

1. assign the exit code of each *watch* to user-defined *tasks* (such
   as `:let-there-be-light` or `:i-cant-get-no-sleep`).

1. for each *task*, check whether any of the executed commands had a
   *non-zero* exit code

1. in case the new state of a *task* differs from its current state,
   execute a command depending on the result

All *watches*, *tasks* and commands as well as their number can be
defined by the user.

This makes MoccaFaux so flexible that it could probably be used for
other tasks as well, such as compiling an application when the files
in a directory change.  In practice, you might be better off to use
dedicated software and APIs like [Gulp.watch] and [inotify].

## The name

As this application was conceived as a substitue for [caffeine], I
originally called it *Muckefuck* (pronounced [ˈmʊkəfʊk]).  This is the
colloquial German term for [coffee substitute].

However, most English speakers would consider this name to contain a
certain bad word (which it doesn't).  I liked the name though, so I
changed it to *Mocca faux*, which might be the French origin for
*Muckefuck*.

## Installation

You need an installation of Java SE 11 (any version for SE 8 probably
works just fine).

If `git` and `lein` are installed on your system, then run this:

```bash
git clone https://github.com/mzuther/moccafaux.git
cd moccafaux
lein clean && lein uberjar
```

Or simply download a pre-compiled JAR file from the [release] section.

## Usage

Open a shell and locate to the MoccaFaux directory.  Then execute:

```bash
# setting MALLOC_ARENA_MAX bounds virtual memory, see
# https://issues.apache.org/jira/browse/HADOOP-7154
MALLOC_ARENA_MAX=4 java -jar ./target/uberjar/moccafaux.jar
```

## Options

MoccaFaux reads its settings from the file
`$HOME/.config/moccafaux/config.json`.  As the name suggests, the
settings are expected to be in standard JSON format.

The settings file constitutes of a map with three key-value pairs (I
don't like the JSON terms, so I'll use their Clojure equivalents
instead):

```javascript
{
  "scheduler": {
    "probing-interval": 60
  },

  "tasks": {
    // ...
  },

  "watches": {
    // ...
  },
}
```

I suggest that you restrict key names to ASCII characters, hyphens and
numbers.  First of all, that looks more LISP-like and let's you feel
like a geek.  Also, and slightly more important, it might prevent
problems during conversion to Clojure/Java data.  You have been
warned.

`probing-interval` sets the number of seconds between repeating the
watches.  This is not restricted in any way, so if you set it to one
second, all watches will be checked once a second.  Let's just hope
that your computer can keep up with this ...

I have found the default of 60 seconds to be a good trade-off between
resource usage and response time.  As a side note, the scheduler's
timing may drift and repetitions will be dropped when the computer is
suspended or under extremely high load – so don't use MoccaFaux when
your main concerns are high reliability or repeatability.

```javascript
  "tasks": {
    "sleep": {
      "enable": {
        "message": "allow computer to save energy",
        "command": "xautolock -time 15 -locker 'systemctl suspend' -detectsleep",
        "fork":    true
      },
      "disable": {
        "message": "keep computer awake",
        "command": "xautolock -exit",
        "fork":    false
      }
    },

    // ...
  }
```

```javascript
  "watches": {
    "video": {
      "enabled":  true,
      "command":  "pgrep -l '^(celluloid|skypeforlinux|vlc)$'",

      "tasks": {
        "dpms": true,
        "sleep": true
      }
    },

    // ...
  }
```

To get started, you can use a copy of `config-SAMPLE.json` and edit it
to taste.

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


[caffeine]:           https://launchpad.net/caffeine
[coffee substitute]:  https://en.wikipedia.org/wiki/Coffee_substitute
[inotify]:            https://en.wikipedia.org/wiki/Inotify
[Gulp.watch]:         https://gulpjs.com/docs/en/getting-started/watching-files

[Martin Zuther]:  http://www.mzuther.de/
[release]:        https://github.com/mzuther/moccafaux/releases
