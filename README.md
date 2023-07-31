# tuples-space-client

[![Build status](https://github.com/cake-lier/tuples-space-client/actions/workflows/release.yml/badge.svg)](https://github.com/cake-lier/tuples-space-client/actions/workflows/release.yml)
[![semantic-release: conventional-commits](https://img.shields.io/badge/semantic--release-conventional_commits-e10098?logo=semantic-release)](https://github.com/semantic-release/semantic-release)
[![Latest release](https://img.shields.io/github/v/release/cake-lier/tuples-space-client)](https://github.com/cake-lier/tuples-space-client/releases/latest/)
[![Scaladoc](https://img.shields.io/github/v/release/cake-lier/tuples-space-client?label=scaladoc)](https://cake-lier.github.io/tuples-space-client/io/github/cakelier)
[![Issues](https://img.shields.io/github/issues/cake-lier/tuples-space-client)](https://github.com/cake-lier/tuples-space-client/issues)
[![Pull requests](https://img.shields.io/github/issues-pr/cake-lier/tuples-space-client)](https://github.com/cake-lier/tuples-space-client/pulls)
[![Codecov](https://codecov.io/gh/cake-lier/tuples-space-client/branch/main/graph/badge.svg?token=UX36N6CU78)](https://codecov.io/gh/cake-lier/tuples-space-client)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=cake-lier_tuples-space-client&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=cake-lier_tuples-space-client)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=cake-lier_tuples-space-client&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=cake-lier_tuples-space-client)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=cake-lier_tuples-space-client&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=cake-lier_tuples-space-client)

## How to use

Add the following line to your `build.sbt` file:

```scala 3
libraryDependencies ++= Seq("io.github.cake-lier" % "tuples-space-client" % "1.0.2")
```

This library is currently available only for scala 3.

## What is this?

This library is the client for a bigger project which allows to create tuple spaces easily and reliably. A tuple space is a
mean to exchange pieces of information between parties while at the same time coordinating the actions of the parties that need
those pieces of information. For example, an entity could suspend its job while waiting for the information to be available, not
differently from how a future-based computation works. When it is available, it can carry on from where it left off. The idea of a
tuple space, which very aptly is a "coordination medium", is to bring this onto distributed systems, which are by definition more
challenging to model, coordinate and in general make work. If you are familiar with any message-oriented middleware, such as
RabbitMQ, this is not different, with the added twist that not only we can send and receive messages, but also wait for
them, decide whether remove them from the pool of messages, probe for their presence or absence etc. A tuple space is just a big
pool of messages, waiting to be read from someone or probed or whatever. Differently from RabbitMQ, we just don't subscribe to
topics, because every receive operation is intended to receive one and only one message.

This repo contains only the client part of this project: the library for communicating with the server from inside your app. The
core elements, such as tuples and templates, are discussed in the corresponding repo. Another repo exists which gives an
implementation to the tuple space server, which the clients can interact with.

## What operations are available then?

The operations that can be sent to the tuple space server are:

| Operation name | Is it "suspensive"? | Is it "bulk"? | What does it do?               |
|----------------|---------------------|---------------|--------------------------------|
| out            | no                  | no            | Inserts a tuple                |
| in             | yes                 | no            | Removes a tuple                |
| rd             | yes                 | no            | Copies a tuple                 |
| no             | yes                 | no            | Checks if tuples do not exist  |
| inp            | no                  | no            | Removes a tuple                |
| rdp            | no                  | no            | Copies a tuple                 |
| nop            | no                  | no            | Checks if tuples do not exist  |
| outAll         | no                  | yes           | Inserts multiple tuples        |
| inAll          | no                  | yes           | Removes multiple tuples        |
| rdAll          | no                  | yes           | Reads multiple tuples          |

Except for the "out" and "outAll" operations, all of them take a template to work. This template is used for matching the tuple or
tuples for the operation in the tuple space. The "suspensive" operations are operations that suspend in case no tuple matching
their template is found in the tuple space (except for the "no" operation, which suspends if one or more tuples matching the
template do exist in the tuple space). If one or more tuples are immediately found (or no tuple is found for the "no" operation) no
suspension is done, but this cannot be checked in any way. The "predicative" variants, i.e. non-suspensive, simply return a special
value if no tuple is found, such as a ```None``` instead of a ```Some[JsonTuple]```. In case multiple tuples are found that match
the template, one at random is chosen, following the "**don't care**" non-determinism. This means that anyway a tuple is chosen,
it is done in a good way. No rules should be specified nor should be enforced for this choice, which should be implemented in the
easiest way and nothing else. The "bulk" operations simply work on multiple tuples at once, inserting, removing or reading multiple
tuples. They cannot be suspensive because if no tuple matching the template is found, simply an empty ```Seq``` is returned.

## How does the client work?

The simplest example of a program that uses the client can be:

```scala 3
for {
  client <- JsonTupleSpace("ws://localhost:443")
  _ <- client.out(0 #: "test" #: JsonNil)
  t <- client.in(complete(int, string))
  // do other things ...
  _ <- client.close()
} ()
```

As you can notice, the connection to the server happens via WebSocket, whether in a secure fashion or not. Only WebSocket is
supported, because is the only mainstream protocol that supports the possibility to exchange a stream of data bidirectionally in
an "event-like" way. Other default parameters that can be specified are the dimension of the messages buffer, the
```ActorSystem``` used for creating the WebSocket client and if to terminate the ```ActorSystem``` on client closing or not,
because this implementation uses [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html) under the hood. The buffer is
used because, similarly to other WebSocket libraries, such as [Socket.IO](https://socket.io/), the guarantees for the exchange of
messages are "**at most once**". This means that:

* if the connection is broken while a message is being sent, then there is no guarantee that the other side has received it and
  there will be no retry upon reconnection. In this case, the ```Future``` representing the operation will become a ```Failure```;
* a disconnected client will buffer messages until reconnection or until the buffer is full, then the messages will be dropped
  starting from the oldest one. In this case, the ```Future```s representing their operations will become ```Failure```s;
* there is no such buffer on the server, which means that any message that was missed by a disconnected client will not be
  transmitted to that client upon reconnection, but the server will process that message anyway.

The guarantee about the ordering of messages relies on the implementation of
[Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html) that the client is using, so check their documentation for
more info. Moreover, as it can be understood from the previous notes, the client can, and will try to reconnect upon
forced disconnection. It will use an exponential back-off to not overwhelm the server with too many requests, but indeed,
until a reconnection will happen or the program is closed, the connection will be retried.

By default, a buffer of 1 is used, because the client can decide to send the reassignment message before the connection is
completely established. A new ```ActorSystem``` is created for each client and, being so, the default is to terminate it when the
client gets closed. It is then **fundamental** to close the client when is no more in use, otherwise, your app won't even terminate
unless you call ```sys.exit()```.

## Can I use it?

Of course, the MIT license is applied to this whole project.
