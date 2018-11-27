import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import javafx.util.Pair;

public class ConnectionHandler implements Runnable {
    public static final String CRLF = "\r\n";
    public static final String DEFAULT_EXTENSIONS = "txt/html";
    private static final Log LOG = LogFactory.getLog(ConnectionHandler.class.getName());
    protected Socket clientSocket;

    public ConnectionHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        try {
            InputStream input = clientSocket.getInputStream();
            OutputStream output = clientSocket.getOutputStream();
            Date time = new Date();
            //todo this is where the request and response are processed
            LOG.debug("parsing request...");
            //parse the request
            HttpParser parser = new HttpParser(clientSocket.getInputStream());
            parser.parseRequest();
            //todo remove this is useless
            Pair<Integer, String> httpReply = getHttpReply(parser);

            validateUrl(parser.getRequestURL());

            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder headerBuilder = new StringBuilder();
            //todo this should happen only when request where fully processed (no exceptions)
            headerBuilder.append("HTTP/1.1 ")
                    .append(httpReply.getValue());

            if (httpReply.getKey() != 501) {
                File file = new File(SimpleSample.WORKING_DIRECTORY + parser.getRequestURL());

                LOG.debug("request received for: " + parser.getRequestURL());
                if (file.exists()) {
                    //in case a directory is requested the content of the response will be dynamically created
                    if (file.isDirectory()) {
                        LOG.debug("file exists and is a directory");

                        String documentStart = CRLF + "<!DOCTYPE HTML>\n"
                                + "<html lang=\"en\">\n"
                                + "<head>\n"
                                + "    <meta charset=\"UTF-8\">\n"
                                + "    <title>File Listing</title>\n"
                                + "</head>\n"
                                + "<h2>Here is your File List</h2>\n"
                                + "<p>Files found in " + parser.getRequestURL() + ":</p>\n";
                        String documentEnd = "</body>\n" + "</html>\r\n";
                        //list files
                        ArrayList<File> filesList = Lists.newArrayList(Objects.requireNonNull(file.listFiles()));
                        File indexHTML = new File(SimpleSample.WORKING_DIRECTORY + "\\index.html");
                        //todo remove check or change response (resource not found etc)
                        if (!indexHTML.exists()) {
                            LOG.error("index.html is missing in the current working directory");
                        }
                        String htmlList = mapFileListToHtmlList(filesList, indexHTML, file);
                        String htmlFile = documentStart + htmlList + documentEnd;

                        LOG.debug("mapped html document: \r\n" + htmlFile);
                        headerBuilder.append(CRLF);
                        contentBuilder.append(headerBuilder.toString());
                        contentBuilder.append(htmlFile);
                    }
                    else {
                        String fileExtension = FilenameUtils.getExtension(file.getName());
                        //simple check for supported extensions based on the request header
                        String acceptedParams = Optional.ofNullable(parser.getHeader("Accept")).orElse(DEFAULT_EXTENSIONS);
                        if (acceptedParams.contains(fileExtension)) {

                            String parsedFile = parseFile(file);
                            //todo the headerBuilder concept should be changed for some OO solution
                            HashCode serversHash = Hashing.sha256().hashString(parsedFile, Charset.forName("UTF-8"));
                            headerBuilder.append("\nETag: \"")
                                    .append(serversHash)
                                    .append("\"\n");

                            //check If-Non-Match is sent add
                            HashCode clientsHash = Optional.ofNullable(parser.getHeader("If-None-Match")).map(HashCode::fromString).orElse(null);
                            if (!Objects.equals(serversHash, clientsHash)) { // server have newer/different file or client haven't sent if-none-match

                                headerBuilder.append(CRLF);
                                contentBuilder.append(headerBuilder.toString());
                                contentBuilder.append(parsedFile);
                            }
                            else {//same hash so requested file is not modified
                                contentBuilder = getContentBuilderForCode(304);
                            }
                        }
                        else {//unsupported file extension
                            contentBuilder = getContentBuilderForCode(404);
                        }
                    }
                }
                else {
                    contentBuilder = getContentBuilderForCode(404);
                }
            }

            output.write(contentBuilder.toString().getBytes());

            output.close();
            input.close();

            LOG.info("Request successfully processed at: " + time);
        } catch (IOException e) {
            LOG.error(e);//report exception somewhere.
            e.printStackTrace();
        }
    }

    private StringBuilder getContentBuilderForCode(int codevalue) {
        String httpReply = HttpParser.getHttpReply(codevalue);
        LOG.info(httpReply);
        return new StringBuilder().append("HTTP/1.1 ").append(httpReply).append(CRLF);
    }

    //dirty fix to prevent client from requesting files outside of the working dir
    //todo rework as this is not working properly, currently the exception is not handled
    private void validateUrl(String requestURL) throws MalformedURLException {
        if (Strings.isNullOrEmpty(requestURL) || requestURL.contains("..")) {
            throw new MalformedURLException("requested url is invalid: " + requestURL);
        }
    }

    private String parseFile(@Nonnull File file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(file.getPath()))) {
            StringBuilder replyBuilder = new StringBuilder();
            replyBuilder.append(CRLF);

            int data = inputStream.read();
            while (data != -1) {
                char theChar = (char) data;
                replyBuilder.append(theChar);
                data = inputStream.read();
            }
            inputStream.close();
            return replyBuilder.toString();
        }
    }

    private String mapFileListToHtmlList(ArrayList<File> filesList, @Nonnull File indexHTML, @Nonnull File currentFolder) {
        List<String> htmlListItems = filesList.stream().map(f -> mapToHtmlListItem(f, currentFolder)).collect(Collectors.toList());
        StringBuilder htmlListBuilder = new StringBuilder();
        String listHead = "<ul>\r\n";
        String listEnd = "</ul>\r\n";
        htmlListBuilder.append(listHead);
        htmlListItems.forEach(htmlListBuilder::append);
        htmlListBuilder.append(mapToHtmlListItem(indexHTML, null));
        htmlListBuilder.append(listEnd);
        String htmlList = htmlListBuilder.toString();
        return htmlList;
    }

    private String mapToHtmlListItem(@Nonnull File file, @Nullable File currentFolder) {
        String listElementStart = "<li>";
        String listElementEnd = "</li>\r\n";
        String elementContentStart = "<a href=\"http://localhost:" + clientSocket.getLocalPort();
        Optional<String> parentPath = Optional.ofNullable(currentFolder).map(File::getName).map(e -> "/" + e);
        String fileName = file.getName(); // todo some path handling would be nicer
        String elementContentEnd = "/" + fileName + "\">" + fileName + "</a>";

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append(listElementStart)
                .append(elementContentStart);

        parentPath.ifPresent(resultBuilder::append);

        resultBuilder.append(elementContentEnd)
                .append(listElementEnd);
        return resultBuilder.toString();
    }

    private Pair<Integer, String> getHttpReply(HttpParser parser) {
        Pair<Integer, String> httpReply = new Pair<>(501, HttpParser.getHttpReply(501));
        if (HttpMethods.GET.equalsIgnoreCase(parser.getMethod())) {
            LOG.debug("got GET request");
            httpReply = new Pair<>(200, HttpParser.getHttpReply(200));
        }
        if (HttpMethods.HEAD.equalsIgnoreCase(parser.getMethod())) {
            LOG.debug("got HEAD request");
            httpReply = new Pair<>(200, HttpParser.getHttpReply(200));
        }
        return httpReply;
    }

}