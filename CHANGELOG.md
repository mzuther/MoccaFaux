# Change Log

*All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com].*


## [Unreleased]
### Changed



## [1.3.1] - 2020-07-27
## Fixed

- fix wrong order of arguments when calling "poll-task-states"



## [1.3.0] - 2020-07-27
### Added

- complete unit tests

### Changed

- extract functionality to "poll-task-states" to improve unit test
  coverage

## Fixed

- fix calling "shell-exec" with empty command



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

## Fixed

- fix reflection warning



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

## Fixed

- kill the scheduler and end MoccaFaux if an exception is thrown



## [1.0.0] - 2020-06-20
### Changed

- This is the first release.


[keepachangelog.com]:  http://keepachangelog.com/
[Unreleased]:          https://github.com/mzuther/moccafaux/tree/develop

[1.0.0]:  https://github.com/mzuther/moccafaux/commits/v1.0.0
[1.1.0]:  https://github.com/mzuther/moccafaux/commits/v1.1.0
[1.2.0]:  https://github.com/mzuther/moccafaux/commits/v1.2.0
[1.3.0]:  https://github.com/mzuther/moccafaux/commits/v1.3.0
[1.3.1]:  https://github.com/mzuther/moccafaux/commits/v1.3.1
