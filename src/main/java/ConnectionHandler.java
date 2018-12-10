import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.client.http.HttpMethods;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

/**
 * @noinspection UnstableApiUsage
 */
public class ConnectionHandler implements Runnable {

    private static final String DEFAULT_EXTENSIONS = "txt/html";
    private static final Log LOG = LogFactory.getLog(ConnectionHandler.class.getName());
    HttpParser parser;
    private Socket clientSocket;

    ConnectionHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    static File fileFromRootFolder(String pathToFile) {
        Path requestedPath = new File(SimpleSample.getWorkingDirectory(), pathToFile).toPath();
        Path parent = Paths.get(SimpleSample.getWorkingDirectory()).normalize();
        boolean requestedRecourseInsideRootDirectory = requestedPath.normalize().startsWith(parent);
        if (requestedRecourseInsideRootDirectory) {
            File requestedFile = requestedPath.toFile();
            if (requestedFile.exists()) {
                return requestedFile;
            }
        }
        LOG.debug("requested resource: " + pathToFile + "\n not found in working directory: " + SimpleSample.getWorkingDirectory());
        return null;
    }

    public void run() {
        try {
            parser = new HttpParser(clientSocket.getInputStream());
            LOG.debug("parsing request...");
            parser.parseRequest();

            HttpResponse response = new HttpResponse();
            if (isGetOrHeadRequest(parser)) {
                File file = fileFromRootFolder(parser.getRequestURL());

                LOG.debug("request received for: " + parser.getRequestURL());
                if (file != null) {
                    StringBuilder parsedFile = null;
                    // in case a directory is requested the content of the response will be dynamically created
                    if (file.isDirectory()) {
                        // no if-none-match or modified-since handling is done for dynamic content
                        parsedFile = processDirectory(file);
                        response.setResponseCode(200);
                    }
                    else {
                        //a file is requested
                        parsedFile = processFile(file, response);
                    }
                    setResponseBody(response, parsedFile);
                }
                else {
                    //file not found
                    response.setResponseCode(404);
                }
            }
            else {
                //unsupported operation
                response.setResponseCode(501);
            }

            OutputStream output = clientSocket.getOutputStream();
            output.write(response.parseHttpResponse());
            output.close();

            LOG.info("Request successfully processed with code: " + response.getResponseCode() + " at: " + new Date());
        } catch (IOException e) {
            //report exception somewhere.
            LOG.error(e);
        }
    }

    private void setResponseBody(@Nonnull HttpResponse response, @Nullable StringBuilder parsedFile) {
        if (HttpMethods.HEAD.equals(parser.getMethod())) {
            if (parsedFile != null) {
                response.putHeader("Content-Length", String.valueOf(parsedFile.toString().getBytes().length));
            }
        }
        else {
            if (response.getResponseCode() == 200) {
                response.setBody(parsedFile);
            }
        }
    }

    @Nonnull
    private StringBuilder processDirectory(@Nonnull File directory) {
        LOG.debug("file exists and is a directory");

        StringBuilder parsedFile = new StringBuilder();
        parsedFile.append(prepareDocumentHead(parser.getRequestURL()));

        //lists files, could lead to java.lang.NullPointerException if java.io.File#listFiles() returns null
        ArrayList<File> filesList = Lists.newArrayList(Objects.requireNonNull(directory.listFiles()));

        parsedFile.append(mapFileListToHtmlList(filesList))
                .append(prepareDocumentEnd());

        LOG.debug("append html document: " + SimpleSample.CRLF + parsedFile + SimpleSample.CRLF);
        return parsedFile;
    }

    private StringBuilder processFile(File file, HttpResponse response) {
        StringBuilder parsedFile = null;
        String fileExtension = FilenameUtils.getExtension(file.getName());
        //simple check for supported extensions based on the request header
        if (isFileExtensionAcceptedByClient(parser, fileExtension)) {

            //cleaner exception handling would be good
            parsedFile = parseFile(file);
            if (parsedFile == null) {
                //error on reading file
                response.setResponseCode(404);
                return parsedFile;
            }
            HashCode serverHash = calculateMD5Hash(parsedFile);

            List<HashCode> isIfMatchHash = retrieveClientHashes(parser.getHeader("If-Match"));
            boolean isIfMatchPresent = !isIfMatchHash.isEmpty();
            List<HashCode> isIfNoneMatchHash = retrieveClientHashes(parser.getHeader("If-None-Match"));
            boolean isIfNoneMatchPresent = !isIfNoneMatchHash.isEmpty();
            Long ifModifiedSince = parseIfModifiedSince(parser.getHeader("If-Modified-Since"));

            Long lastModifiedOnServer = file.lastModified();

            // as described under https://tools.ietf.org/html/rfc7232#section-6
            //todo ObjectOriented solution could be implemented or more functional ie by using filter
            case1(response, serverHash, isIfMatchHash, isIfMatchPresent, isIfNoneMatchHash, isIfNoneMatchPresent);
            case2(response, serverHash, isIfMatchPresent, isIfNoneMatchHash, isIfNoneMatchPresent, ifModifiedSince, lastModifiedOnServer);
            case3(response, serverHash, isIfNoneMatchHash, isIfNoneMatchPresent);
            case4(response, isIfNoneMatchPresent, ifModifiedSince, lastModifiedOnServer);

            if (response.getResponseCode() == null) {
                response.setResponseCode(200);
            }

            appendETagAndLastModifiedDate(response, lastModifiedOnServer, serverHash);
        }
        else {
            //unsupported file extension
            response.setResponseCode(404);
        }
        return parsedFile;
    }

    private void case1(HttpResponse response, HashCode serverHash, List<HashCode> isIfMatchHash, boolean isIfMatchPresent, List<HashCode> isIfNoneMatchHash,
            boolean isIfNoneMatchPresent) {
        if (isIfMatchPresent) {
            if (isIfMatchHash.contains(serverHash)) {
                case3(response, serverHash, isIfNoneMatchHash, isIfNoneMatchPresent);
            }
            else {
                response.setResponseCode(412);
            }
        }
    }

    private void case2(HttpResponse response, HashCode serverHash, boolean isIfMatchPresent, List<HashCode> isIfNoneMatchHash, boolean isIfNoneMatchPresent,
            Long ifModifiedSince, Long lastModifiedOnServer) {
        if (!isIfMatchPresent && ifModifiedSince != null) {
            if (lastModifiedOnServer > ifModifiedSince) {
                case3(response, serverHash, isIfNoneMatchHash, isIfNoneMatchPresent);
            }
            else {
                response.setResponseCode(412);
            }
        }
    }

    private void case3(HttpResponse response, HashCode serverHash, List<HashCode> isIfNoneMatchHash, boolean isIfNoneMatchPresent) {
        if (isIfNoneMatchPresent) {
            if (!isIfNoneMatchHash.contains(serverHash)) {
                // server have newer/different file or client haven't sent if-none-match
                response.setResponseCode(200);
            }
            else {
                response.setResponseCode(304);
            }
        }
    }

    private void case4(HttpResponse response, boolean isIfNoneMatchPresent, Long ifModifiedSince, Long lastModifiedOnServer) {
        if (!isIfNoneMatchPresent && ifModifiedSince != null) {
            if (lastModifiedOnServer > ifModifiedSince) {
                // server have newer/different file or client haven't sent if-none-match
                response.setResponseCode(200);
            }
            else {
                response.setResponseCode(304);
            }
        }
    }

    private void appendETagAndLastModifiedDate(HttpResponse response, Long lastModifiedOnServer, HashCode serverHash) {
        if (response.getResponseCode() == 200 || response.getResponseCode() == 304) {
            response.putHeader("ETag", "\"" + serverHash + "\"");
            response.putHeader("Last-Modified", parseToFormatedDate(lastModifiedOnServer));
        }
    }

    private boolean isGetOrHeadRequest(HttpParser parser) {
        return HttpMethods.GET.equals(parser.getMethod()) || HttpMethods.HEAD.equals(parser.getMethod());
    }

    private Long parseIfModifiedSince(String modified) {
        if (modified != null) {
            SimpleDateFormat format = getDateFormat();
            try {
                return format.parse(modified).getTime();
            } catch (ParseException e) {
                // will be ignored
                LOG.debug(e);
            }
        }
        return null;
    }

    private String parseToFormatedDate(long dateInMillis) {
        return getDateFormat().format(Date.from(Instant.ofEpochMilli(dateInMillis)));
    }

    private SimpleDateFormat getDateFormat() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone(ZoneId.of("GMT")));
        return simpleDateFormat;
    }

    private List<HashCode> retrieveClientHashes(@Nonnull String hashes) {
        List<HashCode> hashCodes = Lists.newArrayList();
        try {
            hashCodes = Optional.of(hashes)
                    .filter(input -> !Strings.isNullOrEmpty(input))
                    .filter(input -> input.length() > 2)
                    .map(input -> input.split(","))
                    .map(Lists::newArrayList).orElse(Lists.newArrayList()).stream()
                    .map(String::trim)
                    .map(token -> token.substring(token.indexOf('\"') + 1, token.lastIndexOf('\"')))
                    .map(HashCode::fromString)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.debug(e);
        }
        return hashCodes;
    }

    private HashCode calculateMD5Hash(StringBuilder parsedFile) {
        return Hashing.sha256().hashString(parsedFile.toString(), Charset.forName("UTF-8"));
    }

    private boolean isFileExtensionAcceptedByClient(HttpParser parser, String fileExtension) {
        return Optional.ofNullable(parser.getHeader("Accept")).orElse(DEFAULT_EXTENSIONS).contains(fileExtension);
    }

    private String prepareDocumentEnd() {
        return "</body>\n" + "</html>\r\n";
    }

    private String prepareDocumentHead(String requestURL) {
        return SimpleSample.CRLF
                + "<!DOCTYPE HTML>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <title>File Listing</title>\n"
                + "</head>\n"
                + "<h2>Here is your File List</h2>\n"
                + "<p>Files found in " + requestURL + ":</p>\n";

    }

    @Nullable
    private StringBuilder parseFile(@Nonnull File file) {
        StringBuilder replyBuilder = new StringBuilder();
        try (InputStream inputStream = Files.newInputStream(Paths.get(file.getPath()))) {
            replyBuilder.append(SimpleSample.CRLF);

            int data = inputStream.read();
            while (data != -1) {
                char theChar = (char) data;
                replyBuilder.append(theChar);
                data = inputStream.read();
            }
            return replyBuilder;
        } catch (IOException e) {
            LOG.debug(e);
            return null;
        }
    }

    private String mapFileListToHtmlList(ArrayList<File> filesList) {
        StringBuilder htmlListBuilder = new StringBuilder();

        String listHead = "<ul>\r\n";
        htmlListBuilder.append(listHead);

        List<String> htmlListItems = filesList.stream().map(this::mapToHtmlListItem).collect(Collectors.toList());
        htmlListItems.forEach(htmlListBuilder::append);

        File indexHTML = fileFromRootFolder("index.html");
        if (indexHTML != null) {
            LOG.debug("index.html is missing in the current working directory");
            htmlListBuilder.append(mapToHtmlListItem(indexHTML));
        }
        htmlListBuilder.append(prepareListEnd());

        return htmlListBuilder.toString();
    }

    private String prepareListEnd() {
        return "</ul>" + SimpleSample.CRLF;
    }

    private String mapToHtmlListItem(@Nonnull File file) {
        StringBuilder resultBuilder = new StringBuilder();

        resultBuilder.append("<li>")
                .append("<a href=\"http://localhost:")
                .append(clientSocket.getLocalPort());

        URI relative = buildRequestedResourceUri(file);

        resultBuilder.append('/')
                .append(relative.toString())
                .append("\">")
                .append(file.getName())
                .append("</a>");

        resultBuilder.append("</li>\r\n");

        return resultBuilder.toString();
    }

    private URI buildRequestedResourceUri(@Nonnull File file) {
        return new File(SimpleSample.getWorkingDirectory()).toURI().relativize(file.toURI());
    }

}