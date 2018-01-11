# Erlang <-> Android Jinterface updated example

So this is a repo that shows that you can use Erlang on Android via Jinterface
to talk to Erlang on a remote node, easily enough (now that all the hard part
is done).

There are hard-coded IP addresses in the Android app - you'll want to change
those to use the ones of your own local network.

Then to launch an Erlang node locally, run (from this directory):

```sh
erl -name server@192.168.1.XX -setcookie test
```

then:

```
c(hello_jinterface).
hello_jinterface:start().
```

Finally run the Android app on the other device.  You will see a ping/pong.
That's the successful connection.  Huzzah!
