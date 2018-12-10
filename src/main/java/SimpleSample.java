import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Lists;

/**
 * todo fix the main java doc
 */
public class SimpleSample {
    static final String CRLF = System.lineSeparator();
    private static final Log LOG = LogFactory.getLog(SimpleSample.class.getName());
    private static final String DEFAULT_WORKING_DIRECTORY = Paths.get("rootFolder").toAbsolutePath().toString();
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_SERVER_RUN_TIME = 10 * 60 * 1000;

    private static String customWorkingDirectory = null;
    private static Integer customPort = null;
    private static Integer customServerRunTime = null;

    public static void main(String[] args) {

        overrideDefaultParameters(Lists.newArrayList(args));

        LOG.info("Starting Server with args = [" + Arrays.toString(args) + "]" + CRLF
                + "server will be listening on port: " + getServerPort() + CRLF
                + "server will be running for " + getServerRunTime() / 60 / 1000 + " minutes" + CRLF
                + "working directory is set to: " + getWorkingDirectory() + CRLF);

        Server server = new Server(getServerPort());
        new Thread(server).start();

        try {
            Thread.sleep(getServerRunTime());
        } catch (InterruptedException e) {
            LOG.error(e);
        }
        LOG.info("Stopping Server");
        try {
            server.stop();
        } catch (IOException e) {
            LOG.error(e);
        }

    }

    public static int getServerPort() {
        return customPort != null ? customPort : DEFAULT_PORT;
    }

    public static int getServerRunTime() {
        return customServerRunTime != null ? customServerRunTime : DEFAULT_SERVER_RUN_TIME;
    }

    public static String getWorkingDirectory() {
        return customWorkingDirectory != null ? customWorkingDirectory : DEFAULT_WORKING_DIRECTORY;
    }

    public static void setCustomWorkingDirectory(String file) {
        customWorkingDirectory = file;
    }

    private static void overrideDefaultParameters(@Nonnull List<String> args) {
        if (!args.isEmpty()) {
            if (args.get(0) != null) {
                customPort = Integer.parseInt(args.get(0));
            }
            if (args.size() > 1 && args.get(1) != null) {
                customServerRunTime = Integer.parseInt(args.get(1)) * 60 * 1000;
            }
            if (args.size() > 2 && args.get(2) != null) {
                customWorkingDirectory = Paths.get(args.get(2)).toAbsolutePath().normalize().toString();
            }
        }
    }
}

