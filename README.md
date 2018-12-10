#Description
This is a HTTP web server build in Java, using mostly the standard JDK.
* This server uses a fixed thread pool with a max pool size of 1000. 
* In case a static html file is served: returns *Last-Modified* date and *ETag* as MD5 Hash
* Additionally handles following request headers: 
  * *If-None-Match* 
  * *If-Match* 
  * *If-Modified-Since*
* as described in `https://tools.ietf.org/html/rfc7232#section-6` with some exceptions

#Startup
* The Server can be build by executing: 
  * *mvn clean compile assembly:single* 
* can be started from bash/cmd from target folder with:
  * *java -jar simple-server-1.0-SNAPSHOT-jar-with-dependencies.jar* 

Additional optional parameters are:
 1. port that will be open for incoming http connections
    (default: *8080*)
 2. desired time until the server is automatically destroyed in minutes 
    (default: *10 min*).
 3. custom root directory 
    (default: *rootFolder*)

#used external resources
HttpParser from: http://www.java2s.com/Code/Java/Network-Protocol/HttpParser.htm
...