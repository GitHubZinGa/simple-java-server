import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.api.client.http.javanet.DefaultConnectionFactory;

public class ServerTest {
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

    @Test(timeout = 5000)
    public void testConnnectionToServerWithFileNotFound() throws IOException {
        //given
        String customWorkingDirectory = Paths.get(".").toAbsolutePath().toString();
        SimpleSample.setCustomWorkingDirectory(customWorkingDirectory);
        System.out.println("servers root set to: " + customWorkingDirectory);

        URL url = new URL("http", "localhost", TEST_PORT, "/index.html");
        System.out.println("connect to: " + url.toString());

        DefaultConnectionFactory factory = new DefaultConnectionFactory();
        HttpURLConnection connection = factory.openConnection(url);
        connection.setRequestMethod("GET");

        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField(0), is(not(nullValue())));
        assertThat(connection.getResponseCode(), is(404));
        assertThat(connection.getResponseMessage(), is(not(nullValue())));
    }

    @Test(timeout = 5000)
    public void testConnnectionToServer() throws IOException, URISyntaxException {
        //given
        HttpURLConnection connection = prepareHttpURLConnection();

        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField(0), is(not(nullValue())));
        assertThat(connection.getResponseCode(), is(200));
        assertThat(connection.getResponseMessage(), is(not(nullValue())));
    }

    @Test(timeout = 5000)
    public void testInvalidIfNoneMatchValue() throws IOException, URISyntaxException {
        //given
        HttpURLConnection connection = prepareHttpURLConnection();
        connection.setRequestProperty("If-None-Match", "\"m\"");
        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField("ETag"), is("\"46a78174d9c0f62fcd96c306bb5a453b296a27f411e0118c347c0895c7841b38\""));
        assertThat(connection.getResponseCode(), is(200));
        assertThat(connection.getResponseMessage(), is(not(nullValue())));
    }

    @Test(timeout = 5000)
    public void testIfNoneMatch() throws IOException, URISyntaxException {
        //given
        HttpURLConnection connection = prepareHttpURLConnection();
        connection.setRequestProperty("If-None-Match", "\"46a78174d9c0f62fcd96c306bb5a453b296a27f411e0118c347c0895c7841b38\"");
        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField("ETag"), is("\"46a78174d9c0f62fcd96c306bb5a453b296a27f411e0118c347c0895c7841b38\""));
        assertThat(connection.getResponseCode(), is(304));
        assertThat(connection.getResponseMessage(), is("Not Modified"));
    }

    @Test(timeout = 5000)
    public void testModifiedSinceWrongFormat() throws IOException, URISyntaxException {
        //given
        HttpURLConnection connection = prepareHttpURLConnection();
        connection.setRequestProperty("If-Modified-Since", "some bla bla");
        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField("ETag"), is("\"46a78174d9c0f62fcd96c306bb5a453b296a27f411e0118c347c0895c7841b38\""));
        assertThat(connection.getHeaderField("Last-Modified"), is("So, 09 Dez 2018 19:24:39 GMT"));
        assertThat(connection.getResponseCode(), is(200));

        InputStream stream = connection.getInputStream();
        String body = IOUtils.toString(stream, "utf-8");
        assertThat(body, is("some test"));
    }

    @Test(timeout = 5000)
    public void testModifiedSince() throws IOException, URISyntaxException {
        //given
        HttpURLConnection connection = prepareHttpURLConnection();
        connection.setRequestProperty("If-Modified-Since", "So, 09 Dez 2018 24:59:59 GMT");
        //when
        connection.connect();

        //then
        assertThat(connection.getHeaderField("ETag"), is("\"46a78174d9c0f62fcd96c306bb5a453b296a27f411e0118c347c0895c7841b38\""));
        assertThat(connection.getResponseCode(), is(304));
        assertThat(connection.getResponseMessage(), is("Not Modified"));
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