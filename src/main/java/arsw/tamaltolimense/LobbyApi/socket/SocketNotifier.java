package arsw.tamaltolimense.LobbyApi.socket;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class SocketNotifier {

    // URL base del servicio de sockets, por ejemplo: http://lobby-socket.azurewebsites.net
    @Value("${socket.service.url}")
    private String socketServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void notifyLobbyUpdated(String lobbyName, Lobby lobby) {
        String url = socketServiceUrl + "/notify/lobbyUpdated";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Lobby> entity = new HttpEntity<>(lobby, headers);
        restTemplate.postForEntity(url, entity, Void.class);
    }

    public void notifyGameStarted(String lobbyName) {
        String url = socketServiceUrl + "/notify/gameStarted";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Enviamos el nombre del lobby en el body
        HttpEntity<String> entity = new HttpEntity<>(lobbyName, headers);
        restTemplate.postForEntity(url, entity, Void.class);
    }

    public void notifyRoundEnded(String lobbyName, int remainingRounds) {
        String url = socketServiceUrl + "/notify/roundEnded";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("lobbyName", lobbyName);
        body.put("remainingRounds", remainingRounds);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, Void.class);
    }
}
