package arsw.tamaltolimense.LobbyApi.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;

@Controller
public class SocketIOProxyController {

    @Value("${socketio.port:65000}")
    private int socketioPort;

    private static final Logger logger = LoggerFactory.getLogger(SocketIOProxyController.class);

    @GetMapping("/socket.io/**")
    public void handleSocketIOGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.info("Recibida solicitud GET para Socket.IO: {}", request.getRequestURI());
        // Redireccionar la petición al puerto Socket.IO
        response.sendRedirect("http://localhost:" + socketioPort + request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
    }

    @PostMapping("/socket.io/**")
    public void handleSocketIOPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.info("Recibida solicitud POST para Socket.IO: {}", request.getRequestURI());
        // Redireccionar la petición al puerto Socket.IO
        response.sendRedirect("http://localhost:" + socketioPort + request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
    }
}
