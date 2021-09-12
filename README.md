# MoccaFaux

_Adapt power management to changes in the environment._

![Screenshot](./resources/cover-photo-hapishane.png)

**MoccaFaux** can be used to prevent the activation of screen savers
and power-saving modes when certain conditions are met. It was
inspired by **[caffeine]**, but is much more flexible.

In fact, **MoccaFaux** is a scheduler that executes commands (called
_watches_), looks at their exit codes and then decides whether to
execute another set of commands (called _tasks_):

1. execute all _watches_ (such as `:listening-to-music` or
   `:system-backup-is-running`) and record their exit codes

1. assign exit code of each _watch_ to one or more _tasks_ (such as
   `:keep-computer-awake` or `:disable-screensaver`)

1. update state of each _task_:

   - `:active` if _any_ of the assigned _watches_ returned a
     _non-zero_ exit code

   - `:idle` in any other case

1. in case the state of a _task_ differs from its previous state,
   execute a command depending on its new state

1. wait, rinse and repeat the above

On exit, **MoccaFaux** _tries_ to execute the `:idle` command for
every task. This usually works, but as it happens in a critical phase
during shutdown, you should not rely on this behaviour.

All _watches_, _tasks_ and commands as well as their number can be
defined by the user.

This makes **MoccaFaux** very flexible and it could probably be used
for other tasks as well, such as compiling an application when the
files in a directory change. In practice, however, you might be better
off using dedicated software and APIs such as **[Gulp.watch]** and
**[inotify]**.

_Please note that the scheduler's timing may drift and repetitions
will be dropped when the computer is suspended or under extremely high
load – so please do not use **MoccaFaux** when your main concerns are
high reliability or repeatability._

## Motivation

To be honest, controlling screensaver and power management of Linux
desktops is quite a mess. Despite some conventions, there seems to be
no universal standard, and applications and tools often resort to
kludges.

I wanted to keep my computer awake when syncing large files, but there
seemed to be no solution that would survive the ravages of time. So I
decided that disabling power management and screensaver was my best
option and started to write **MoccaFaux**.

## Name

As this application was conceived as a substitue for **[caffeine]**, I
originally called it **Muckefuck** (pronounced [ˈmʊkəfʊk]). This is a
colloquial German term for [coffee substitute].

On second thought, English speakers might reason that this name
contains a certain "bad" word (it doesn't). I liked the name, though,
so I changed it to **Mocca faux**, which some believe to be the French
origin of **Muckefuck**.

## Installation

You need an installation of Java. I currently use OpenJDK 11, but any
Java version from 8 probably works just fine:

```bash
# Debian-based systems
sudo apt-get install default-jre
```

### Pre-built

Simply download a pre-compiled JAR file from the [release] section and
copy `moccafaux-x.x.x.jar` to a place of your liking.

### Compilation

To compile **MoccaFaux**, you'll also need _Git_ and _Leiningen_:

```bash
# Debian-based systems
sudo apt-get install git leiningen
```

Then clone the repository and compile the application:

```bash
git clone https://github.com/mzuther/moccafaux.git
cd moccafaux
lein clean && lein uberjar
```

Finally, copy `target/uberjar/moccafaux.jar` to a place of your liking.

## Usage

Open a shell and locate to the **MoccaFaux** directory. Then execute:

```bash
# setting MALLOC_ARENA_MAX bounds virtual memory, see
# https://issues.apache.org/jira/browse/HADOOP-7154
MALLOC_ARENA_MAX=4 java -jar moccafaux.jar
```

You will probably start **MoccaFaux** automatically or from a menu.
When problems occur, however, run it in a shell. It prints status
messages to `stdout` that will help you with problem fixing.

By default, **MoccaFaux** adds an icon to the system tray bar. This
icon has three states and changes depending on whether _no_, _some_ or
_all_ of the defined tasks are `:idle`.

## Options

**MoccaFaux** reads its settings from the file
`$HOME/.config/moccafaux/config.edn`. As the file extension suggests,
settings are expected to be in Clojure's [EDN] format. You can think
of it as an enhanced version of JSON (use `;` for comments).

To get started, use a copy of `config-SAMPLE.edn` (found in the
repository's root directory) and edit to taste. For it to work, you
have to **disable your system's own power management** and install
some tools:

```bash
# Debian-based systems (pgrep, xautolock and xset)
sudo apt-get install procps xautolock x11-xserver-utils

# optional stuff (pactl and zpool)
sudo apt-get install pulseaudio-utils zfsutils-linux
```

### General structure

The settings file constitutes of a map with three key-value pairs:

```clojure
{
   :settings {
      :add-traybar-icon true
      :probing-interval 60
   }

   :tasks {
      ;; see below
   }

   :watches {
      ;; see below
   }
}
```

I suggest that you restrict key names to ASCII characters, hyphens and
numbers. However, Clojure does not force you to do so.

### Settings

#### `:add-traybar-icon`

Set this to `false` if you do not want **MoccaFaux** to add an icon to
the system tray bar.

#### `:probing-interval`

Sets the interval in seconds for repeating the _watch_ commands. I
have found 60 seconds to be a good trade-off between resource usage
and response time. But this interval is not restricted in any way, so
if you set it to one second (great for testing), all _watches_ will be
checked once per second. Let's just hope that your computer can keep
up with this ...

### Tasks

For something to actually happen, you have to define at least one
_task_. _Tasks_ are basically switches that are turned on and off by
**MoccaFaux**.

They are defined in a map with two keys (`:active` and `:idle`), which
in turn consist of maps containing three keys (`:message`, `:command`
and `:fork`):

```clojure
  :tasks {
     :sleep {
        :active {
           :message "allow computer to save energy"
           :command "xautolock -time 10 -locker 'systemctl suspend' -detectsleep"
           :fork    true
        }

        :idle {
           :message "keep computer awake"
           :command "xautolock -exit"
           :fork    false
        }
    }

    ;; and so on ...
  }
```

#### `:active` and `:idle`

When _any_ of the assigned _watch_ commands returns a _non-zero_ exit
code, the command defined under `:active` is executed. Otherwise, the
`:idle` command is executed.

_When you start **MoccaFaux**, each task will be executed once
according to the current state of your watches._

#### `:command`

This is the actual command and is executed in a shell environment
compatible to the Bourne shell (`sh -c "your | commands && come ; here"`).
This way, you can use your beloved pipes and logical tests.

_Note: backslashes have to be quoted by doubling (`\\`)._

#### `:fork`

**MoccaFaux** normally runs a command and waits for it to exit.
However, some commands need to continue running in the background. In
such a case, set `:fork` to `true`. Note that forked commands are not
monitored, so you have to kill them manually when watch states change.
When **MoccaFaux** exits, forked commands _should_ be killed
automatically, but this is not guaranteed.

#### `:message`

Use `:message` to describe what is happening in plain text. It will
be shown on the command line and may help you with debugging (or
understanding the EDN file in a year or so).

### Watches

_Watches_ are commands that are run periodically. When their exit
code changes, they may trigger the toggling of a _task_.

_Watches_ are defined in a map of three keys (`:enabled`, `:command`
and `:tasks`):

```clojure
  :watches {
     :video {
        :enabled true
        :command "pgrep -l '^(celluloid|skypeforlinux|vlc)$'"
        :tasks {
           :dpms  true
           :sleep true
        }
    }

    ;; and so on ...
  }
```

#### `:enabled`

Set to `false ` if you do not want a _watch_ to be executed. Use this
when you only need it occasionally or want to keep your time-proven
settings where you can easily find them in the future.

#### `:command`

This is identical to its twin in _tasks_ except that it must exit
(**MoccaFaux** needs the exit code), so forking is not supported.

#### `:tasks`

Map of keys naming the _tasks_ that should be updated whenever the
state of this _watch_ changes. Each key's value should be set to
`true`.

You may also set a key to `false`. This is identical to not adding
the _task_ to this map.

## Contributors

- [Martin Zuther][]: maintainer

## License

_Copyright (c) 2020-2021 [Martin Zuther] and contributors_

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

[caffeine]: https://launchpad.net/caffeine
[coffee substitute]: https://en.wikipedia.org/wiki/Coffee_substitute
[edn]: https://learnxinyminutes.com/docs/edn/
[gulp.watch]: https://gulpjs.com/docs/en/getting-started/watching-files
[inotify]: https://en.wikipedia.org/wiki/Inotify
[martin zuther]: http://www.mzuther.de/
[release]: https://github.com/mzuther/moccafaux/releases
