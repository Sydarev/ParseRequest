package ru.netology;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static ServerSocket server;
    private static int serverPort;
    private static boolean isServerCreated = false;
    final static List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js");
    final static Map<String, Handler> handlers = new ConcurrentHashMap<>();
    final static ExecutorService executorService = Executors.newFixedThreadPool(64);
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static String requestLine;

    public static ServerSocket getServer(int port) throws IOException {
        if (!isServerCreated) {
            serverPort = port;
            isServerCreated = true;
            server = new ServerSocket(port);
        }
        return server;
    }

    public static void serverAnswer(String[] parts, BufferedOutputStream out) throws IOException {

        final var path = parts[1];
        if (!validPaths.contains(path)) {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
            return;
        }

        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // special case for classic
        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
            return;
        }

        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    public static void addHandler(String requestType, String pathFile, Handler handler) {
        boolean flag = false;
        for (Map.Entry<String, Handler> entry : handlers.entrySet()) {
            if (entry.getKey().equals(requestType + pathFile)) {
                flag = true;
                break;
            }
        }
        if (!flag) handlers.put(requestType + pathFile, handler);
    }

    public static Handler getHandler(Request request) {
        var findKey = request.getMethod() + request.getPath();
        for (Map.Entry<String, Handler> entry : handlers.entrySet()) {
            if (entry.getKey().equals(findKey)) {
                return entry.getValue();
            }
        }
        return new Handler() {
            @Override
            public void handle(Request string, BufferedOutputStream responseServer) throws IOException {
                responseServer.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n").getBytes());
                responseServer.flush();
            }
        };
    }

    public static void start(ServerSocket serverSocket) {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                executorService.execute(new Thread(() -> {
                    try (final var in = new BufferedInputStream(socket.getInputStream());
                         final var out = new BufferedOutputStream(socket.getOutputStream())) {
                        Request request = new Request();
                        parseRequest(out, in, request);
//                        request.parseQueryParam();
//                        System.out.println("all the query: " + request.getQueryParams());
//                        System.out.println("value = " + request.getQueryParam("value"));
                        System.out.println("body name value: "+request.getPostParam("value"));
                        System.out.println("all the bodies: "+request.getPostParams());
                        Handler handler = Server.getHandler(request);
                        handler.handle(request, out);
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void parseRequest(BufferedOutputStream out, BufferedInputStream in, Request request) throws IOException {
        final var allowedMethods = List.of(GET, POST);
        final var limit = 4096;
        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            badRequest(out);
            return;
        }
        requestLine = new String(Arrays.copyOf(buffer, requestLineEnd));
        request.setRequestLine(requestLine);
        final var requestLineArr = requestLine.split(" ");
        if (requestLineArr.length != 3) {
            badRequest(out);
            return;
        }
        final var method = requestLineArr[0];
        if (!allowedMethods.contains(method)) {
            badRequest(out);
            return;
        }
        final var path = requestLineArr[1];
        if (!path.startsWith("/")) {
            badRequest(out);
            return;
        }
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            badRequest(out);
            return;
        }
        in.reset();
        in.skip(headersStart);
        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
        request.addHeaders(headers);
        request.showHeaders();
//        System.out.println(headers);
        if(!method.equals(GET)){
            in.skip(headersDelimiter.length);
            final var contentLength = request.extractHeader("Content-Length");
            if(contentLength.isPresent()){
                final var length = Integer.parseInt(contentLength.get());
                final var bodyBytes = in.readNBytes(length);
                final var body = new String(bodyBytes);
                request.addBodies(body);
            }
        }
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }
}
