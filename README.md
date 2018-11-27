#Description
This is a HTTP web server build in Java, using mostly the standard JDK.

This server uses a fixed thread pool.
Returns ETag in case a static html file is served.
Checks for If-None-Match contained in the request header.


#Startup
The Server can be build by executing *mvn clean compile assembly:single*
and started from bash/cmd with *java -jar simple-server-1.0-SNAPSHOT-jar-with-dependencies.jar* from target folder

additional optional parameters are:
1. port (to change default port from 8080)
2. desired runtime of the server in minutes.

#used external resources
HttpParser from: http://www.java2s.com/Code/Java/Network-Protocol/HttpParser.htm
...