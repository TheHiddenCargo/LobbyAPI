package arsw.tamaltolimense.LobbyApi.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "lobbies")
public class Lobby {
    @Id
    private String nombre;
    private String contraseña;
    private List<String> jugadores;
    private int jugadoresConectados;
    private int jugadoresListos;

    // Constructor vacío
    public Lobby() {}

    // Constructor con parámetros
    public Lobby( String nombre, String contraseña, List<String> jugadores, int jugadoresConectados, int jugadoresListos) {
        this.nombre = nombre;
        this.contraseña = contraseña;
        this.jugadores = jugadores;
        this.jugadoresConectados = jugadoresConectados;
        this.jugadoresListos = jugadoresListos;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getJugadoresConectados() {
        return jugadoresConectados;
    }

    public int getJugadoresListos() {
        return jugadoresListos;
    }

    public List<String> getJugadores() {
        return jugadores;
    }



    public String getContraseña() {
        return contraseña;
    }


    public void setContraseña(String contraseña) {
        this.contraseña = contraseña;
    }

    public void setJugadores(List<String> jugadores) {
        this.jugadores = jugadores;
    }

    public void setJugadoresConectados(int jugadoresConectados) {
        this.jugadoresConectados = jugadoresConectados;
    }

    public void setJugadoresListos(int jugadoresListos) {
        this.jugadoresListos = jugadoresListos;
    }

}

