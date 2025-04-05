package arsw.tamaltolimense.LobbyApi.socket;

import arsw.tamaltolimense.LobbyApi.exception.LobbyException;
import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.service.LobbyService;
import com.corundumstudio.socketio.SocketIOServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LobbyIOServer implements CommandLineRunner {
    private final SocketIOServer server;

    private LobbyService lobbyService;

    private static final String LOBBY = "lobby";

    private static final String USUARIO = "usuario";


    @Autowired
    public LobbyIOServer(@Qualifier("LobbySocketIOServer") SocketIOServer server, LobbyService lobbyService) {
        this.server = server;
        this.lobbyService = lobbyService;
    }


    @Override
    public void run(String... args) throws Exception {
        server.start();
        System.out.println("Servidor Socket.IO Lobby iniciado en el puerto " + server.getConfiguration().getPort());

        server.addConnectListener(client -> {
            String lobby = client.getHandshakeData().getSingleUrlParam(LOBBY);
            String usuario = client.getHandshakeData().getSingleUrlParam(USUARIO);
            System.out.println("Cliente conectado al lobby: " + usuario + lobby);
            client.joinRoom(lobby);


        });
        server.addEventListener("connect_user",Map.class,(client,data,ackRequest) ->{
            String usuario = client.getHandshakeData().getSingleUrlParam(USUARIO);
            try {
                String lobby = client.getHandshakeData().getSingleUrlParam(LOBBY);
                Lobby lobbyConectada = lobbyService.getLobby(lobby);
                System.out.println("Cliente conectado al lobby: " + lobby);
                lobbyService.agregarUsuario(usuario,lobbyConectada);
                server.getRoomOperations(lobby).sendEvent("jugadores_conectados",
                        lobbyService.getPlayers(lobbyConectada));

            } catch (LobbyException e) {
                client.sendEvent("error", e.getMessage());
            }
            if (ackRequest.isAckRequested()) ackRequest.sendAckData("conectar_usuario aceptado " + usuario);
        });

        server.addEventListener("disconnect_lobby",Map.class,(client,data,ackRequest) ->{
            String usuario = client.getHandshakeData().getSingleUrlParam(USUARIO);
            String lobby = client.getHandshakeData().getSingleUrlParam(LOBBY);
            Lobby lobbyConectada = lobbyService.getLobby(lobby);
            System.out.println("Cliente conectado al lobby: " + lobby);
            lobbyService.desconectarUsuario(usuario,lobbyConectada);
            server.getRoomOperations(lobby).sendEvent("jugadores_conectados",
                    lobbyService.getPlayers(lobbyConectada));
            client.disconnect();

            if (ackRequest.isAckRequested()) ackRequest.sendAckData("conectar_usuario aceptado " + usuario);
        });



        server.addEventListener("user_ready",Map.class,(client,data,ackRequest) ->{
            String nombreLobby = client.getHandshakeData().getSingleUrlParam(LOBBY);
            String usuario = client.getHandshakeData().getSingleUrlParam(USUARIO);
            Lobby lobby = lobbyService.getLobby(nombreLobby);
            lobbyService.usuarioListo(usuario,lobby);
            server.getRoomOperations(nombreLobby).sendEvent("jugadores_conectados",
                    lobbyService.getPlayers(lobby));
            if (ackRequest.isAckRequested()) ackRequest.sendAckData("usuario_ready " + usuario);
        });

        server.addEventListener("user_not_ready",Map.class,(client,data,ackRequest) ->{
            String nombreLobby = client.getHandshakeData().getSingleUrlParam(LOBBY);
            String usuario = client.getHandshakeData().getSingleUrlParam(USUARIO);
            Lobby lobby = lobbyService.getLobby(nombreLobby);
            lobbyService.usuarioNoListo(usuario,lobby);
            server.getRoomOperations(nombreLobby).sendEvent("jugadores_conectados",
                    lobbyService.getPlayers(lobby));
            if (ackRequest.isAckRequested()) ackRequest.sendAckData("usuario_ready " + usuario);
        });

        server.addEventListener("start_round",Map.class,(client,data,ackRequest) ->{
            String nombreLobby = client.getHandshakeData().getSingleUrlParam(LOBBY);
            Lobby lobby = lobbyService.getLobby(nombreLobby);
            if(lobbyService.usuariosListos(lobby))server.getRoomOperations(nombreLobby).sendEvent("round_started",
                    lobbyService.getContainer(lobby));
            else client.sendEvent("error","No todos los jugadores estan listos");
            if (ackRequest.isAckRequested()) ackRequest.sendAckData("Iniciar partida aceptado");
        });



        server.addDisconnectListener(client -> {
            String lobby = client.getHandshakeData().getSingleUrlParam(LOBBY);
            System.out.println("Cliente desconectado del lobby: " + lobby + client.getSessionId());
        });


        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));


    }
}
