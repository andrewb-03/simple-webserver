/** 
* @author Andrew Brockenborough
* 2/17/25 - A. Brockenborough coded the methods for WebServer.java and added comments.
*/ 

package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import server.config.MimeTypes;

/**
 * A  multithreaded HTTP server which handles:
 * - GET, HEAD, PUT, DELETE requests
 * - Basic authentication (401, 403) using .password files
 * - Reading the exact Content-Length for PUT
 */
public class WebServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final String documentRoot;
    private final MimeTypes mimeTypes;
    private final ExecutorService threadPool;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java server.WebServer <port> <document-root>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String documentRoot = args[1];

        try (WebServer server = new WebServer(port, documentRoot, MimeTypes.getDefault())) {
            server.listen();
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    public WebServer(int port, String documentRoot, MimeTypes mimeTypes) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.documentRoot = documentRoot;
        this.mimeTypes = mimeTypes;
        // Thread pool to handle multiple clients
        this.threadPool = Executors.newFixedThreadPool(10);

        System.out.println("Server started on port " + port);
        System.out.println("Document root: " + documentRoot);
    }

    /**
     * This continously accepts incoming connections and handles them in separate threads.
     */
    public void listen() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleRequest(clientSocket));
            } catch (IOException e) {
                System.err.println("Error accepting connection: " + e.getMessage());
            }
        }
    }

    /**
     * This handles an individual HTTP request.
     */
    private void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            // 1) Request Line
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                sendResponse(out, 400, "Bad Request", "Invalid request received");
                return;
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length != 3) {
                sendResponse(out, 400, "Bad Request", "Malformed request");
                return;
            }

            String method = requestParts[0];
            String requestedPath = requestParts[1];
            String httpVersion = requestParts[2];

            // 2) We only support HTTP/1.1
            if (!"HTTP/1.1".equals(httpVersion)) {
                sendResponse(out, 505, "HTTP Version Not Supported", "Only HTTP/1.1 is supported");
                return;
            }

            // 3) Reads headers until a blank line
            List<String> headerLines = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                headerLines.add(line);
            }

            // 4) Parse headers into a map
            Map<String, String> headers = new HashMap<>();
            for (String headerLine : headerLines) {
                int colonPos = headerLine.indexOf(":");
                if (colonPos > 0) {
                    String headerName = headerLine.substring(0, colonPos).trim().toLowerCase();
                    String headerValue = headerLine.substring(colonPos + 1).trim();
                    headers.put(headerName, headerValue);
                }
            }

            // 5) Constructs the file path
            File file = new File(documentRoot, requestedPath);

            // 6) Checks for .password => Basic Auth
            File passwordFile = new File(file.getParentFile(), ".password");
            if (passwordFile.exists()) {
                // If no Authorization => 401
                if (!headers.containsKey("authorization")) {
                    sendUnauthorizedResponse(out);
                    return;
                }
                // Otherwise checks credentials => 403 if its invalid
                if (!checkAuth(headers.get("authorization"), passwordFile)) {
                    sendResponse(out, 403, "Forbidden", "Invalid credentials");
                    return;
                }
            }

            // 7) Grabs contentLength (for PUT)
            int contentLength = 0;
            if (headers.containsKey("content-length")) {
                try {
                    contentLength = Integer.parseInt(headers.get("content-length"));
                } catch (NumberFormatException e) {
                    // If its invalid, contentLength remains 0
                }
            }

            // 8) Dispatch by method
            switch (method) {
                case "GET" -> handleGet(file, out);
                case "HEAD" -> handleHead(file, out);
                case "PUT" -> handlePut(file, out, in, contentLength);
                case "DELETE" -> handleDelete(file, out);
                default -> sendResponse(out, 405, "Method Not Allowed", "Unsupported HTTP method");
            }

        } catch (IOException e) {
            System.err.println("Error handling request: " + e.getMessage());
        }
    }

    /**
     * GET request => read file and returns 200 or 404.
     */
    private void handleGet(File file, OutputStream out) throws IOException {
        if (!file.exists()) {
            sendResponse(out, 404, "Not Found", "File not found");
            return;
        }

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String contentType = mimeTypes.getMimeTypeFromExtension(getFileExtension(file));

        sendResponse(out, 200, "OK", new String(fileBytes), contentType, fileBytes.length);
    }

    /**
     * HEAD request => same as GET except no body in the response.
     */
    private void handleHead(File file, OutputStream out) throws IOException {
        if (!file.exists()) {
            sendResponse(out, 404, "Not Found", "File not found");
            return;
        }

        long fileSize = file.length();
        String contentType = mimeTypes.getMimeTypeFromExtension(getFileExtension(file));
        sendResponse(out, 200, "OK", "", contentType, (int) fileSize);
    }

    /**
     * PUT request => reads exactly contentLength bytes into the file.
     */
    private void handlePut(File file, OutputStream out, BufferedReader in, int contentLength) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Reads contentLength bytes from the input stream
            for (int i = 0; i < contentLength; i++) {
                int c = in.read();
                if (c == -1) {
           
                    break;
                }
                fos.write(c);
            }
            sendResponse(out, 201, "Created", "File successfully created or updated");
        } catch (IOException e) {
            sendResponse(out, 500, "Internal Server Error", "Error writing file");
        }
    }

    /**
     * DELETE request => removes the file if the file exists.
     */
    private void handleDelete(File file, OutputStream out) throws IOException {
        if (!file.exists()) {
            sendResponse(out, 404, "Not Found", "File not found");
            return;
        }

        if (file.delete()) {
            // 204 No Content
            sendResponse(out, 204, "No Content", "");
        } else {
            sendResponse(out, 500, "Internal Server Error", "Failed to delete file");
        }
    }

    /**
     * Sends a simple text/plain response with the specified status code and body.
     */
    private void sendResponse(OutputStream out, int statusCode, String statusMessage, String body) throws IOException {
        sendResponse(out, statusCode, statusMessage, body, "text/plain", body.length());
    }

    /**
     * Sends an HTTP response with standard headers.
     */
    private void sendResponse(OutputStream out, int statusCode, String statusMessage,
                              String body, String contentType, int contentLength) throws IOException {

        PrintWriter writer = new PrintWriter(out);
        writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        writer.println("Date: " + new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").format(new Date()));
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + contentLength);
        writer.println();
        writer.flush();

        if (statusCode != 204) {
            out.write(body.getBytes());
            out.flush();
        }
    }

    /**
     * Sends a 401 Unauthorized with WWW-Authenticate for Basic Auth.
     */
    private void sendUnauthorizedResponse(OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(out);
        writer.println("HTTP/1.1 401 Unauthorized");
        writer.println("Date: " + new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").format(new Date()));
        writer.println("WWW-Authenticate: Basic realm=\"667 Server\"");
        writer.println("Content-Length: 0");
        writer.println();
        writer.flush();
    }

    /**
     * Verifies the Authorization header is "Basic <base64>" and matches .password.
     */
    private boolean checkAuth(String authHeader, File passwordFile) {
        if (authHeader == null) {
            return false;
        }
        String lower = authHeader.toLowerCase();
        if (!lower.startsWith("basic ")) {
            return false;
        }

        String base64Part = authHeader.substring("Basic ".length()).trim();
        String decoded = new String(Base64.getDecoder().decode(base64Part)); // example: "abrockenborough:password1"

        return isValidUser(decoded, passwordFile);
    }

    /**
     * Confirms the decoded "username:password" appears in .password.
     */
    private boolean isValidUser(String credentials, File passwordFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(passwordFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().equals(credentials.trim())) {
                    return true;
                }
            }
        } catch (IOException e) {
            // This can log or handle differently if needed
        }
        return false;
    }

    /**
     * Extracts the file extension.
     */
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return (lastDot == -1) ? "" : name.substring(lastDot + 1);
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        threadPool.shutdown();
        System.out.println("Server shut down.");
    }
}
