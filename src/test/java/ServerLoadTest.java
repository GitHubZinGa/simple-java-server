import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.api.client.http.javanet.DefaultConnectionFactory;
import com.google.common.collect.Lists;

public class ServerLoadTest {
    public static final int TEST_PORT = 8080;
    Server unit = new Server(SimpleSample.getServerPort());

    @Before
    public void startServer() {
        new Thread(unit).start();
    }

    @After
    public void stopServer() throws IOException {
        if (unit != null && !unit.isStopped()) {
            unit.stop();
        }
    }

    @Test(timeout = 5000000)
    public void testConnnectionToServer() throws IOException, URISyntaxException {
        //given
        int numberOfConnections = 100000;
        ArrayList<HttpURLConnection> connectionPool = Lists.newArrayList();
        for (int i = 0; i < numberOfConnections; i++) {
            HttpURLConnection connection = prepareHttpURLConnection();

        }
        //when
        connectionPool.parallelStream().peek(connection -> {
            try {
                connection.connect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).forEach(connection -> {
            try {
                connection.getResponseMessage();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        //then -> expect no exception
    }

    private HttpURLConnection prepareHttpURLConnection() throws URISyntaxException, IOException {
        String customWorkingDirectory = Paths.get(this.getClass().getResource(".").toURI()).toAbsolutePath().toString();
        SimpleSample.setCustomWorkingDirectory(customWorkingDirectory);
        System.out.println("servers root set to: " + customWorkingDirectory);

        URL url = new URL("http", "localhost", TEST_PORT, "/test.html");
        System.out.println("connect to: " + url.toString());

        DefaultConnectionFactory factory = new DefaultConnectionFactory();
        HttpURLConnection connection = factory.openConnection(url);
        connection.setRequestMethod("GET");
        return connection;
    }
}