package arsw.tamaltolimense.LobbyApi.config;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LobbySocketIoConfig extends BaseSocketIOConfig{


    @Value("${socket-server.lobby.host}")
    private String host;

    @Value("${socket-server.lobby.port}")
    private Integer port;

    @Value("${socket-server.origin}")
    private String origin;

    @Bean(name = "LobbySocketIOServer")
    public SocketIOServer bidSocketIOServer() {
        return createSocketServer(host, port, origin);
    }

}