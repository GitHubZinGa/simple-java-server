import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class LogicTest {
    @Test
    public void testGeneralFileLogic() {
        String requestUrl = "/../../../";
        //requestedPath = requestedPath.replaceFirst("/", "/../../");
        Path requestedPath = new File(SimpleSample.getWorkingDirectory(), requestUrl).toPath();
        Path parent = Paths.get(SimpleSample.getWorkingDirectory()).normalize();
        boolean requestedRecourceOutsideOfRootDirectory = requestedPath.normalize().startsWith(parent);
        assertThat(requestedRecourceOutsideOfRootDirectory, is(false));
    }

    @Test
    public void testFileFromRootFolder() {
        String requestUrl = "/../../../";
        File file = ConnectionHandler.fileFromRootFolder(requestUrl);
        assertThat(file, is(nullValue()));
    }

}
