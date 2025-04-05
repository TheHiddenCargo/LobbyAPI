package arsw.tamaltolimense.LobbyApi.exception;

public class LobbyException extends Exception{

    public static final String NO_LOBBY = "No existe el lobby";
    public static final String JUGADOR_CONECTADO = "El jugador ya esta conectado";
    public static final String JUGADOR_NO_EN_LOBBY = "Jugador no conectado al lobby";
    public static final String MAX_CONTAINERS = "Se ha excedido el limite de contenedores";

    public LobbyException(String message){
        super(message);
    }
}
