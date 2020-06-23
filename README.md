# MoccaFaux

*Adapt power management to changes in the environment.*

**MoccaFaux** can be used to prevent the activation of screen savers
and power-saving modes when certain conditions are met.  It was
inspired by **[caffeine]**, but is much more flexible.

In fact, **MoccaFaux** is a scheduler that executes commands (called
*watches*), looks at their exit codes and then decides whether to
execute another set of commands (called *tasks*):

1. execute *watches* one by one and record their exit codes

1. assign the exit code of each *watch* to one or more *tasks* (such
   as `let-there-be-light` or `i-cant-get-no-sleep`)

1. update state of each *task*:

   * `enable` if *any* of the assigned *watches* returned a *non-zero*
     exit code

   * `disable` in any other case

1. in case the state of a *task* differs from its previous state,
   execute a command depending on its current state

1. wait, rinse and repeat the above

All *watches*, *tasks* and commands as well as their number can be
defined by the user.

This makes **MoccaFaux** very flexible and it could probably be used
for other tasks as well, such as compiling an application when the
files in a directory change.  In practice, you might be better off
using dedicated software and APIs like **[Gulp.watch]** and
**[inotify]**.

*Please note that the scheduler's timing may drift and repetitions
will be dropped when the computer is suspended or under extremely high
load – so please do not use **MoccaFaux** when your main concerns are
high reliability or repeatability.*

## The name

As this application was conceived as a substitue for **[caffeine]**, I
originally called it **Muckefuck** (pronounced [ˈmʊkəfʊk]).  This is
the colloquial German term for [coffee substitute].

However, most English speakers would consider this name to contain a
certain bad word (which it doesn't).  I liked the name though, so I
changed it to **Mocca faux**, which some believe is the French origin
of **Muckefuck**.

## Installation

You need an installation of Java SE 11 (any version from SE 8 probably
works just fine).

If `git` and `lein` are installed on your system, run this:

```bash
git clone https://github.com/mzuther/moccafaux.git
cd moccafaux
lein clean && lein uberjar
```

Or simply download a pre-compiled JAR file from the [release] section.
Finally, copy `moccafaux.jar` to a place of your liking and you're
done.

## Usage

Open a shell and locate to the **MoccaFaux** directory.  Then execute:

```bash
# setting MALLOC_ARENA_MAX bounds virtual memory, see
# https://issues.apache.org/jira/browse/HADOOP-7154
MALLOC_ARENA_MAX=4 java -jar ./target/uberjar/moccafaux.jar
```

## Options

**MoccaFaux** reads its settings from the file
`$HOME/.config/moccafaux/config.json`.  As the name suggests, the
settings are expected to be in standard JSON format.

To get started, use a copy of `config-SAMPLE.json` (found in the
repository's root directory) and edit it to taste.

### General structure

*I find the JSON terms a bit non-descript, so I'll use their Clojure
equivalents instead.*

The settings file constitutes of a map with three key-value pairs:

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
numbers.  First of all, that looks very LISP-like and let's you feel
like a geek.  Also, and slightly more important, it prevents problems
during conversion to Clojure/Java data.  You have been warned.

### Scheduler

#### `probing-interval`

Sets the the interval for repeating the *watch* commands in seconds.
I have found 60 seconds to be a good trade-off between resource usage
and response time.  But the interval is not restricted in any way, so
if you set it to one second, all *watches* will be checked once a
second.  Let's just hope that your computer can keep up with this ...

### Tasks

For something to actually happen, you have to define at least one
*task*.  *Tasks* are basically switches that are turned on and off by
**MoccaFaux**.

They are defined by a map with two keys (`enable` and `disable`),
which in turn consist of maps containing three keys (`message`,
`command` and `fork`):

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

#### `enable` and `disable`

When *any* of the assigned *watch* commands returns a *non-zero* exit
code, the command defined under `enable` is executed .  Otherwise, the
command defined under `disable` is executed.

#### `command`

`command` is the actual command and is executed in a shell environment
compatible to the Bourne shell (`sh -c "your | commands && come ;
here"`).  This way, you can use your beloved pipes and logical tests.

*Note: backslashes have to be quoted by doubling (`\\`).*

#### `fork`

Set to `true` if you want the shell that is executing the command to
continue running in the background.  This shell will usually be killed
when you exit **MoccaFaux**.

#### `message`

Use `message` to describe what is happening in plain text.  It will be
shown on the command line and may help you with debugging (or
understanding the JSON file in a year or so).

*When you start **MoccaFaux**, each task will be executed once
according to the current state of your watches.*

### Watches

*Watches* are the commands that are run periodically.  When their exit
code changes, they can trigger the toggling of a *task*.

*Watches* are defined by a map which contains three keys (`enabled`,
`command` and `tasks`):

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

#### `enabled`

Set to `false ` if you do not want a *watch* to be executed.  Use this
if you only need it occasionally or want to keep your time-proven
settings where you can easily find them in the future.

#### `command`

`command` is identical to its twin in *tasks* except that it cannot be
forked.

#### `tasks`

Map of keys naming the *tasks* that should be updated when the state
of this *watch* changes.  Set the value of each key to `true`.

You may also set a key to `false`.  This is identical to not adding
the *task* to this map.

## License

*Copyright (c) 2020 [Martin Zuther]*

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

**Thank you for using free software!**


[caffeine]:           https://launchpad.net/caffeine
[coffee substitute]:  https://en.wikipedia.org/wiki/Coffee_substitute
[inotify]:            https://en.wikipedia.org/wiki/Inotify
[Gulp.watch]:         https://gulpjs.com/docs/en/getting-started/watching-files

[Martin Zuther]:  http://www.mzuther.de/
[release]:        https://github.com/mzuther/moccafaux/releases
