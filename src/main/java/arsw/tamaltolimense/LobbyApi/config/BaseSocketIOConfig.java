package arsw.tamaltolimense.LobbyApi.config;


import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;


public abstract class BaseSocketIOConfig {


    protected SocketIOServer createSocketServer(String host, int port,String origin) {
        System.out.println("SocketIOServer" + host + ":" + port + origin);
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setOrigin(origin);

        config.setAllowCustomRequests(true);
        config.setUpgradeTimeout(10000);
        config.setPingTimeout(60000);
        config.setPingInterval(25000);

        return new SocketIOServer(config);
    }
}
