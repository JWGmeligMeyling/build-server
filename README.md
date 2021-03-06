[![Stories in Ready](https://badge.waffle.io/devhub-tud/devhub.png?label=ready&title=Ready)](https://waffle.io/devhub-tud/devhub)
[![Build Status](https://travis-ci.org/devhub-tud/build-server.svg?branch=master)](https://travis-ci.org/devhub-tud/build-server)
[![Coverage Status](https://coveralls.io/repos/github/devhub-tud/build-server/badge.svg?branch=master)](https://coveralls.io/github/devhub-tud/build-server?branch=master)
[![Dependency Status](https://www.versioneye.com/user/projects/569e8ac3ec6e6a000e1bb98e/badge.svg?style=flat)](https://www.versioneye.com/user/projects/569e8ac3ec6e6a000e1bb98e)
DevHub Build-server
======
DevHub is a software system designed to give students a simple practical introduction into modern software development. It provides an environment in which they can work on practical assignments without setting up their own (private) code repository or a Continuous Integration server. The environment is also designed to give students a working simple workflow largely based on GitHub's pull requests system. 

This repository contains the build server used for Devhub. The build server has a REST API and builds any git repositories safely in isolated Docker containers. The build result is then returned through a web hook.

Build a development system
------------

On deployed systems running under Linux the docker host can be on the same system as the build server instance. For development under Windows and OS X, a docker host VM is required (for example [Boot2Docker](http://boot2docker.io)). The build server instance clones the repositories to a working directory shared with the Docker container, for this to work in a VM, we need to make a shared folder between the host computer and the Docker host VM.

```sh
brew install docker
brew install boot2docker
sudo mkdir /workspace
boot2docker init
VBoxManage sharedfolder add "boot2docker-vm" --name "workspace" --hostpath "/workspace"
boot2docker up
```

Then ssh into the boot2docker virtual machine to mount the shared folder. 
```sh
boot2docker ssh
sudo mkdir /workspace
sudo mount -t vboxsf -o uid=1000,gid=50 workspace /workspace
```

Add docker images to Docker
------------
On the Docker host, navigate to a folder containing a `Dockerfile` and run the following command. (This example is for the 'java-maven' Dockerfile in the repository)

```sh
build -t java-maven .
```

Example request to build
-----------

```
POST /api/builds HTTP/1.1
Host: localhost:8082
Authorization: Basic TWljaGFlbExhcHRvcDp0MmhMQ1hWRQ==
Content-Type: application/json
Cache-Control: no-cache

{ "source": { "type": "GIT", "repositoryUrl": "https://github.com/SERG-Delft/jpacman-template.git", "branchName": "master", "commitId": "405318ba729de7b47de50fca6b7c65fe52ef1811" }, "instruction": { "type": "MAVEN", "plugins": [], "withDisplay": true, "phases": ["test"] }, "callbackUrl": "http://localhost:8082/callback", "timeout": 99999 }
```
