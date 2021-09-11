# Change Log

_All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com]._

<!--- ---------------------------------------------------------------------- -->

## [Unreleased]

### Changed

<!--- ---------------------------------------------------------------------- -->

## [1.5.0] - 2021-09-11

### Added

- show idle tasks on stdout

- show changed idle watches on stdout

### Changed

- add applications to sample configuration

- update dependencies

- update documentation

<!--- ---------------------------------------------------------------------- -->

## [1.4.1] - 2021-01-11

### Changed

- add applications and new section to sample configuration

### Fixed

- detect remote SSH logins correctly (new version of sshd)

<!--- ---------------------------------------------------------------------- -->

## [1.4.0] - 2020-08-15

### Added

- add tray bar icon that changes according to state of tasks (_green_
  if at least one task is idle, _red-yellow-green_ if all tasks are
  idle); please note that Java currently reports the wrong icon size
  for i3bar

- show current task states in tray bar menu

- allow users to disable tray bar icon

- check status of watches in parallel threads

- clean up when application ends

### Changed

- breaking changes (please see README):

  - rename task states to improve code readability

  - read settings from EDN file

  - change section "scheduler" in configuration file to "settings"

- correctly licence files

- update documentation

<!--- ---------------------------------------------------------------------- -->

## [1.3.1] - 2020-07-27

### Fixed

- fix wrong order of arguments when calling "poll-task-states"

<!--- ---------------------------------------------------------------------- -->

## [1.3.0] - 2020-07-27

### Added

- complete unit tests

### Changed

- extract functionality to "poll-task-states" to improve unit test
  coverage

### Fixed

- fix calling "shell-exec" with empty command

<!--- ---------------------------------------------------------------------- -->

## [1.2.0] - 2020-07-13

### Added

- add command line help and version display

- add Leiningen debug profile

- add a first unit test

### Changed

- improve console logging

- return process object from "shell-exec"

- place guards around IO operations

- re-factor code

### Fixed

- fix reflection warning

<!--- ---------------------------------------------------------------------- -->

## [1.1.0] - 2020-06-23

### Added

- let users define tasks and do not limit the number of tasks

- add complete documentation

- display an error message if the settings file cannot be opened or is
  broken

### Changed

- completely change the structure of the settings file

- do not skip the first state update when MoccaFaux is started

- drop the default settings

- rename the sample settings file

### Fixed

- kill the scheduler and end MoccaFaux if an exception is thrown

<!--- ---------------------------------------------------------------------- -->

## [1.0.0] - 2020-06-20

### Changed

- This is the first release.

<!--- ---------------------------------------------------------------------- -->

[keepachangelog.com]: http://keepachangelog.com/
[unreleased]: https://github.com/mzuther/moccafaux/tree/develop
[1.0.0]: https://github.com/mzuther/moccafaux/commits/v1.0.0
[1.1.0]: https://github.com/mzuther/moccafaux/commits/v1.1.0
[1.2.0]: https://github.com/mzuther/moccafaux/commits/v1.2.0
[1.3.0]: https://github.com/mzuther/moccafaux/commits/v1.3.0
[1.3.1]: https://github.com/mzuther/moccafaux/commits/v1.3.1
[1.4.0]: https://github.com/mzuther/moccafaux/commits/v1.4.0
[1.4.1]: https://github.com/mzuther/moccafaux/commits/v1.4.1
[1.5.0]: https://github.com/mzuther/moccafaux/commits/v1.5.0
