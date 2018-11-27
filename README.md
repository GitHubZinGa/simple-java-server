#Description#
This is a HTTP web server build in Java, using mostly the standard JDK.

This server uses a fixed thread pool.
Returns ETag in case a static html file is served.
Checks for If-None-Match contained in the request header.


#Startup#
The Server can be started from bash/cmd with "java -jar your_server.jar" 
currently without any additional parameters.

#used external resources#
HttpParser from: http://www.java2s.com/Code/Java/Network-Protocol/HttpParser.htm
...