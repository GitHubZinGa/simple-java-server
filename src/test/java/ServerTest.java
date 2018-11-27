import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.api.client.http.javanet.DefaultConnectionFactory;

public class ServerTest {
    Server unit = new Server(SimpleSample.DEFAULT_PORT);

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

    @Test(timeout = 5000)
    public void testConnnectionToServerWithFileNotFound() throws IOException {
        //given
        SimpleSample.WORKING_DIRECTORY = ServerTest.class.getResource(".").getFile();
        URL url = new URL("http", "localhost", SimpleSample.DEFAULT_PORT, "/index.html");

        DefaultConnectionFactory factory = new DefaultConnectionFactory();
        HttpURLConnection connection = factory.openConnection(url);
        connection.setRequestMethod("GET");

        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField(0), is(not(nullValue())));
        assertThat(connection.getResponseCode(), is(404));
        assertThat(connection.getResponseMessage(), is(not(nullValue())));
        stopServer();
    }

    @Test(timeout = 5000)
    public void testConnnectionToServer() throws IOException {
        //given
        SimpleSample.WORKING_DIRECTORY = ServerTest.class.getResource(".").getFile();
        URL url = new URL("http", "localhost", SimpleSample.DEFAULT_PORT, "/list_example.html");

        DefaultConnectionFactory factory = new DefaultConnectionFactory();
        HttpURLConnection connection = factory.openConnection(url);
        connection.setRequestMethod("GET");

        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField(0), is(not(nullValue())));
        assertThat(connection.getResponseCode(), is(200));
        assertThat(connection.getResponseMessage(), is(not(nullValue())));
        stopServer();
    }
}
