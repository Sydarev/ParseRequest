package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedInputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class Request {
    private BufferedInputStream inputStream;
    private List<String> headers = new ArrayList<>();

    private String requestLine;
    private List<NameValuePair> params;
    private List<String> bodies = new ArrayList<>();

    public void setRequestLine(String requestLine) {
        this.requestLine = requestLine;
    }

    public void parseQueryParam() throws MalformedURLException {
        this.params = URLEncodedUtils.parse(URI.create("http://localhost:9999" + requestLine.split(" ")[1]), "UTF-8");
    }

    public String getRequestLine() {
        return requestLine;
    }

    public void addHeader(String header) {
        headers.add(header);
    }

    public void addHeaders(List<String> headers) {
        this.headers.addAll(headers);
    }

    public OptionalInt getHeader(String head) {
        for (String header : headers) {
            if (header.startsWith(head)) {
                return OptionalInt.of(Integer.parseInt(header.substring(head.length()+2, header.length())));
            }
        }
        return null;
    }

    public void addBodies(String bodies) {
        this.bodies = List.of(bodies.split("&"));
    }
    public String getPostParam(String name){
        for(String body: bodies){
            if(body.startsWith(name)){
                return body.substring(body.indexOf("=")+1, body.length());
            }
        }
        return null;
    }
    public List<String> getPostParams() {
        if(bodies != null) return bodies;
        return null;
    }

    public void showHeaders() {
        for (String str : headers) {
            System.out.println(str);
        }
    }

    public List<NameValuePair> getQueryParams() throws MalformedURLException {
        if (params == null) parseQueryParam();
        return params;
    }
    public Optional<String> extractHeader(String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    public String getQueryParam(String name) throws MalformedURLException {
        var params = getQueryParams();
        for (NameValuePair entry : params) {
            if (entry.getName().equals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String getPath() {
        String path = requestLine.split(" ")[1];
//        return URLEncodedUtils.CONTENT_TYPE;
        int index = path.indexOf('?');
        if (index == -1) return path;
        return path.substring(0, index);
    }

    public String getMethod() {
        return requestLine.split(" ")[0];
    }
}
