package arsw.tamaltolimense.LobbyApi.socket;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.repository.LobbyRepository;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

@Component
public class LobbySocketService {

    private SocketIOServer server;
    private final Map<String, String> sessionToNickname = new HashMap<>();
    private final Map<String, String> sessionToLobby = new HashMap<>();

    @Autowired
    private LobbyRepository lobbyRepository;

    @PostConstruct
    public void init() {
        String webPort = System.getenv("PORT");
        int port = webPort != null ? Integer.parseInt(webPort) : 8080;
        Configuration config = new Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(80);
        config.setContext("/socket.io");

        // Mejor configuración de CORS
        config.setOrigin("*");

        // Configuración para depuración
        config.setRandomSession(false);


        // Solo una vez
        config.setAuthorizationListener(data -> true);
        config.setAllowCustomRequests(true);
        config.setPingTimeout(60000);
        config.setPingInterval(25000);


        server = new SocketIOServer(config);

        // Configurar eventos de conexión y desconexión
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());

        // Configurar eventos específicos del lobby
        server.addEventListener("joinLobby", JoinLobbyData.class, onJoinLobby());
        server.addEventListener("leaveLobby", LeaveLobbyData.class, onLeaveLobby());
        server.addEventListener("playerReady", PlayerReadyData.class, onPlayerReady());
        server.addEventListener("playerNotReady", PlayerNotReadyData.class, onPlayerNotReady());
        server.addEventListener("chatMessage", ChatMessageData.class, onChatMessage());

        server.start();
    }

    @PreDestroy
    public void destroy() {
        if (server != null) {
            server.stop();
        }
    }

    private ConnectListener onConnected() {
        return client -> {
            System.out.println("Cliente conectado: " + client.getSessionId());
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String sessionId = client.getSessionId().toString();
            String nickname = sessionToNickname.get(sessionId);
            String lobbyName = sessionToLobby.get(sessionId);

            if (nickname != null && lobbyName != null) {
                // Manejar la desconexión del jugador
                Lobby lobby = lobbyRepository.findByNombre(lobbyName);
                if (lobby != null) {
                    lobby.setJugadoresConectados(lobby.getJugadoresConectados() - 1);
                    // Si el jugador estaba listo, hay que restar también
                    lobbyRepository.save(lobby);

                    // Notificar a todos en la sala que el jugador se desconectó
                    server.getRoomOperations(lobbyName).sendEvent("playerLeft", new PlayerLeftData(nickname));
                }
            }

            // Limpiar los mapas
            sessionToNickname.remove(sessionId);
            sessionToLobby.remove(sessionId);

            System.out.println("Cliente desconectado: " + sessionId);
        };
    }

    private DataListener<JoinLobbyData> onJoinLobby() {
        return (client, data, ackRequest) -> {
            System.out.println("Jugador " + data.getNickname() + " intenta unirse al lobby: " + data.getLobbyName());

            // Guardar la información de sesión
            String sessionId = client.getSessionId().toString();
            sessionToNickname.put(sessionId, data.getNickname());
            sessionToLobby.put(sessionId, data.getLobbyName());

            // Unir al cliente a la sala
            client.joinRoom(data.getLobbyName());

            // Notificar a todos en la sala que un jugador se unió
            server.getRoomOperations(data.getLobbyName()).sendEvent("playerJoined",
                    new PlayerJoinedData(data.getNickname()));

            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("Te has unido al lobby: " + data.getLobbyName());
            }
        };
    }

    private DataListener<LeaveLobbyData> onLeaveLobby() {
        return (client, data, ackRequest) -> {
            String sessionId = client.getSessionId().toString();
            String nickname = sessionToNickname.get(sessionId);
            String lobbyName = data.getLobbyName();

            if (nickname != null && lobbyName != null) {
                // Quitar al jugador de la sala
                client.leaveRoom(lobbyName);

                // Notificar a todos en la sala que el jugador se fue
                server.getRoomOperations(lobbyName).sendEvent("playerLeft",
                        new PlayerLeftData(nickname));

                // Actualizar en la base de datos
                Lobby lobby = lobbyRepository.findByNombre(lobbyName);
                if (lobby != null) {
                    lobby.setJugadoresConectados(lobby.getJugadoresConectados() - 1);
                    lobbyRepository.save(lobby);
                }

                // Limpiar el mapa de lobby para esta sesión
                sessionToLobby.remove(sessionId);
            }

            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("Has abandonado el lobby: " + lobbyName);
            }
        };
    }

    private DataListener<PlayerReadyData> onPlayerReady() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            String nickname = data.getNickname();

            Lobby lobby = lobbyRepository.findByNombre(lobbyName);
            if (lobby != null) {
                lobby.setJugadoresListos(lobby.getJugadoresListos() + 1);
                lobbyRepository.save(lobby);

                // Notificar a todos en la sala que el jugador está listo
                server.getRoomOperations(lobbyName).sendEvent("playerReady",
                        new PlayerReadyData(nickname, lobbyName));

                // Comprobar si todos los jugadores están listos
                if (lobby.getJugadoresListos() == lobby.getJugadoresConectados()) {
                    server.getRoomOperations(lobbyName).sendEvent("allPlayersReady", lobbyName);
                }
            }
        };
    }

    private DataListener<PlayerNotReadyData> onPlayerNotReady() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            String nickname = data.getNickname();

            Lobby lobby = lobbyRepository.findByNombre(lobbyName);
            if (lobby != null && lobby.getJugadoresListos() > 0) {
                lobby.setJugadoresListos(lobby.getJugadoresListos() - 1);
                lobbyRepository.save(lobby);

                // Notificar a todos en la sala que el jugador no está listo
                server.getRoomOperations(lobbyName).sendEvent("playerNotReady",
                        new PlayerNotReadyData(nickname, lobbyName));
            }
        };
    }

    private DataListener<ChatMessageData> onChatMessage() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();

            // Reenviar el mensaje a todos en la sala
            server.getRoomOperations(lobbyName).sendEvent("chatMessage", data);
        };
    }

    // Métodos para enviar eventos desde el controlador
    public void notifyLobbyUpdated(String lobbyName, Lobby lobby) {
        server.getRoomOperations(lobbyName).sendEvent("lobbyUpdated", lobby);
    }

    public void notifyGameStarted(String lobbyName) {
        server.getRoomOperations(lobbyName).sendEvent("gameStarted", lobbyName);
    }

    public void notifyRoundEnded(String lobbyName, int remainingRounds) {
        server.getRoomOperations(lobbyName).sendEvent("roundEnded",
                new RoundEndedData(lobbyName, remainingRounds));
    }
}

// Clases de datos para los eventos
class JoinLobbyData {
    private String nickname;
    private String lobbyName;

    public JoinLobbyData() {}

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class LeaveLobbyData {
    private String lobbyName;

    public LeaveLobbyData() {}

    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class PlayerJoinedData {
    private String nickname;

    public PlayerJoinedData() {}

    public PlayerJoinedData(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}

class PlayerLeftData {
    private String nickname;

    public PlayerLeftData() {}

    public PlayerLeftData(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}

class PlayerReadyData {
    private String nickname;
    private String lobbyName;

    public PlayerReadyData() {}

    public PlayerReadyData(String nickname, String lobbyName) {
        this.nickname = nickname;
        this.lobbyName = lobbyName;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class PlayerNotReadyData {
    private String nickname;
    private String lobbyName;

    public PlayerNotReadyData() {}

    public PlayerNotReadyData(String nickname, String lobbyName) {
        this.nickname = nickname;
        this.lobbyName = lobbyName;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class ChatMessageData {
    private String nickname;
    private String lobbyName;
    private String message;

    public ChatMessageData() {}

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

class RoundEndedData {
    private String lobbyName;
    private int remainingRounds;

    public RoundEndedData() {}

    public RoundEndedData(String lobbyName, int remainingRounds) {
        this.lobbyName = lobbyName;
        this.remainingRounds = remainingRounds;
    }

    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
    public int getRemainingRounds() { return remainingRounds; }
    public void setRemainingRounds(int remainingRounds) { this.remainingRounds = remainingRounds; }
}