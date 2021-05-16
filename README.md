# Erlang <-> Java on Android using Jinterface and Unix Domain Sockets

This repo shows how to use the Jinterface Java library (included in Erlang)
on Android, allowing communication between Java and an Erlang node, launched
locally on the same device by the same Android app.

With Erlang normally, the Erlang Port Mapper Daemon (epmd) would have to
be launched first to allow two nodes to communicate with each other. The
implementation of epmd, listening system-wide to a TCP socket on port 4369
by default, is not well adapted to the Android platform, in particular to
cover the simple case of local communications between several nodes running
within the same Android application, or when having several Erlang-based
Android applications running totally independently on the same host.

This project is proposing another approach, taking advantage of the Android
application sandboxing model which isolates application data from each other,
to provide a custom node registration and lookup based on Unix Domain Sockets
created in the app-specific data storage (instead of the usual TCP-based epmd).

This custom implementation requires some modifications to the Erlang Jinterface
Java package, which was designed originally to make mandatory requests to epmd.

Run the Android app on a device, you will see a ping/pong in the logs.
That's the successful connection without epmd. Huzzah!
