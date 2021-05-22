-module(hello_jinterface).
-export([start/0, pong/0]).

pong() ->
    receive
        stop ->
            io:format("Pong finished...~n", []),
            %% Finally stop the node smoothly
            init:stop();
        {Ping_PID, ping} ->
            io:format("Ping~n", []),
            Ping_PID ! {self(), pong},
            pong()
    end.

start() ->
    register(pong, spawn(hello_jinterface, pong, [])).
