package arsw.tamaltolimense.LobbyApi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;

@Controller
public class SocketIOProxyController {

    private static final Logger logger = LoggerFactory.getLogger(SocketIOProxyController.class);
    private static final int SOCKETIO_PORT = 65000;

    @GetMapping(value = "/socket.io/**", produces = MediaType.ALL_VALUE)
    public void proxySocketIOGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.info("Proxy GET para Socket.IO: {}{}",
                request.getRequestURI(),
                request.getQueryString() != null ? "?" + request.getQueryString() : "");

        proxyRequest(request, response, "GET");
    }

    @PostMapping(value = "/socket.io/**", produces = MediaType.ALL_VALUE)
    public void proxySocketIOPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.info("Proxy POST para Socket.IO: {}{}",
                request.getRequestURI(),
                request.getQueryString() != null ? "?" + request.getQueryString() : "");

        proxyRequest(request, response, "POST");
    }

    private void proxyRequest(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {
        HttpURLConnection connection = null;
        try {
            // Construir URL para el Socket.IO interno
            String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
            URL url = new URL("http://localhost:" + SOCKETIO_PORT + request.getRequestURI() + queryString);

            // Configurar la conexión
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);

            // Copiar todos los headers de la solicitud original
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!"host".equalsIgnoreCase(headerName)) { // No copiar el header Host
                    connection.setRequestProperty(headerName, request.getHeader(headerName));
                }
            }

            // Si es POST, copiar el body
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                try (InputStream inputStream = request.getInputStream();
                     OutputStream outputStream = connection.getOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
            }

            // Obtener la respuesta
            int statusCode = connection.getResponseCode();
            response.setStatus(statusCode);

            // Copiar los headers de la respuesta
            for (String headerName : connection.getHeaderFields().keySet()) {
                if (headerName != null) { // getHeaderFields incluye un null para el status line
                    response.setHeader(headerName, connection.getHeaderField(headerName));
                }
            }

            // Copiar el contenido de la respuesta
            try (InputStream inputStream = statusCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
                 OutputStream outputStream = response.getOutputStream()) {
                if (inputStream != null) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
            }

        } catch (IOException e) {
            logger.error("Error en proxy Socket.IO: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error de proxy: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}