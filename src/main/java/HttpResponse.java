import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Maps;

public class HttpResponse {
    private static final Log LOG = LogFactory.getLog(HttpResponse.class.getName());
    private static final String HTTP_1_1 = "HTTP/1.1 ";
    private Map<String, String> headers = Maps.newHashMap();
    private StringBuilder body;
    private Integer responseCode;

    public Map<String, String> getHeaders() {
        return headers;
    }

    public StringBuilder getBody() {
        return this.body;
    }

    public void setBody(StringBuilder body) {
        this.body = body;
    }

    public StringBuilder appendBodyContent(String content) {
        if (content != null && this.body == null) {
            this.body = new StringBuilder();
        }
        return this.body.append(content);
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public void putHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public String buildHeader() {
        StringBuilder headerBuilder = new StringBuilder()
                .append(HTTP_1_1)
                .append(HttpParser.getHttpReply(getResponseCode()))
                .append(SimpleSample.CRLF);
        getHeaders().entrySet().stream()
                .map(e -> String.format("%s: %s%s", e.getKey(), e.getValue(), SimpleSample.CRLF))
                .forEach(headerBuilder::append);
        return headerBuilder.toString();
    }

    public byte[] parseHttpResponse() {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append(this.buildHeader());
        contentBuilder.append(this.getBody());
        LOG.debug(contentBuilder.toString());
        return contentBuilder.toString().getBytes();
    }
}
