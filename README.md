## Tcp chat


### How to run

Running the server:
`sbt "runMain server.Main 127.0.0.1 3333"`

Running the client connecting to the server:

`sbt "runMain client.Client 127.0.0.1 3333 haghard"`

`sbt "runMain client.Client 127.0.0.1 3333 legorby"`


Client commands:

text send to all connected users

`/users` lists logged in users.

`/quit` disconnect and terminate client.



### TO DO 

Grpc and websocket


https://doc.akka.io/docs/akka/current/stream/stream-io.html#streaming-tcp


https://github.com/johanandren/akka-streams-tcp-chat.git


https://github.com/wiringbits/safer.chat

## Chat links

1: Chat app (web socket)
MergeHub.source[String].toMat(BroadcastHub.sink[String])
https://markatta.com/codemonkey/posts/chat-with-akka-http-websockets/


2.
Web socket to shard region: https://github.com/henrikengstrom/sa-2017-akka.git

3.
Building a Reactive, Distributed Messaging Server in Scala and Akka with WebSockets.

https://medium.com/@nnnsadeh/building-a-reactive-distributed-messaging-server-in-scala-and-akka-with-websockets-c70440c494e3


4.
https://bartekkalinka.github.io/2017/02/12/Akka-streams-source-run-it-publish-it-then-run-it-again.html
https://github.com/bartekkalinka/akka-streams-workshop/blob/master/src/main/scala/edu/Lesson5.scala

5.
http://ticofab.io/akka-http-websocket-example/

6.
http://ticofab.io/distributed-websocket-server-with-akka-http/

7.
https://towardsdatascience.com/websocket-streaming-with-scala-fab2feb11868

8.
https://medium.com/@nnnsadeh/building-a-reactive-distributed-messaging-server-in-scala-and-akka-with-websockets-c70440c494e3

9.
Implementing the Reactive Manifesto with Akka - Adam Warski: https://www.youtube.com/watch?v=LXEhQPEupX8

10.
https://github.com/playframework/play-samples/tree/2.8.x/play-scala-chatroom-example/

11.
https://github.com/wiringbits/safer.chat

12.
https://ticofab.io/blog/distributed_websocket_server_with_akka_http/
https://github.com/ticofab/akka-http-distributed-websockets.git

13.
https://blog.knoldus.com/gatling-for-websocket-protocol/

14.
https://github.com/raboof/akka-http-backpressure/blob/master/src/main/scala/streams/TcpServer.scala

15.
https://github.com/raboof/strategies-for-streaming/blob/master/src/main/scala/streams/TcpServer.scala

16.
https://github.com/seyuf/play-swagger-silhouette-actors

17.
https://github.com/guilhebl/scala-chat-server

18.
https://blog.lunatech.com/posts/2021-03-15-typing-your-actors

19.
https://amencke.medium.com/writing-an-online-chat-service-using-event-sourcing-with-scala-and-akka-typed-actors-565ab5887f58