import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * todo fix the main java doc
 */
public class SimpleSample {
    public static int DEFAULT_PORT = 9000;
    public static int RUN_TIME = 10 * 60 * 1000;
    public static String WORKING_DIRECTORY = null;

    private static final Log LOG = LogFactory.getLog(SimpleSample.class.getName());

    public static void main(String[] args) {
        if(args[0]!=null){
            DEFAULT_PORT= Integer.parseInt(args[0]);
        }
        if(args[1] != null) {
            RUN_TIME = Integer.parseInt(args[1]);
        }
        LOG.info("Starting Server with args = [" + Arrays.toString(args) + "]\n "
                + "server will be listening on port: " + DEFAULT_PORT + "\n "
                + "server will be running for " + RUN_TIME / 60 / 1000 + " minutes");

        //todo allow to set custom path
        WORKING_DIRECTORY = Paths.get(".").toAbsolutePath().normalize().toString();
        //WORKING_DIRECTORY = SimpleSample.class.getResource("index.html").getFile();

        Server server = new Server(DEFAULT_PORT);
        new Thread(server).start();

        try {
            Thread.sleep(RUN_TIME);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOG.info("Stopping Server");
        try {
            server.stop();
        } catch (IOException e) {
            LOG.error(e);
        }

    }
}

