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
    private int numeroDeRondas;
    private int maxJugadoresConectados;
    private String modoDeJuego;

    public Lobby() {}

    public Lobby(String nombre, String contraseña, List<String> jugadores, int jugadoresConectados, int jugadoresListos, int numeroDeRondas, int maxJugadoresConectados, String modoDeJuego) {
        this.nombre = nombre;
        this.contraseña = contraseña;
        this.jugadores = jugadores;
        this.jugadoresConectados = jugadoresConectados;
        this.jugadoresListos = jugadoresListos;
        this.numeroDeRondas = numeroDeRondas;
        this.maxJugadoresConectados = maxJugadoresConectados;
        this.modoDeJuego = modoDeJuego;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getContraseña() { return contraseña; }
    public void setContraseña(String contraseña) { this.contraseña = contraseña; }
    public List<String> getJugadores() { return jugadores; }
    public void setJugadores(List<String> jugadores) { this.jugadores = jugadores; }
    public int getJugadoresConectados() { return jugadoresConectados; }
    public void setJugadoresConectados(int jugadoresConectados) { this.jugadoresConectados = jugadoresConectados; }
    public int getJugadoresListos() { return jugadoresListos; }
    public void setJugadoresListos(int jugadoresListos) { this.jugadoresListos = jugadoresListos; }
    public int getNumeroDeRondas() { return numeroDeRondas; }
    public void setNumeroDeRondas(int numeroDeRondas) { this.numeroDeRondas = numeroDeRondas; }
    public int getMaxJugadoresConectados() { return maxJugadoresConectados; }
    public void setMaxJugadoresConectados(int maxJugadoresConectados) { this.maxJugadoresConectados = maxJugadoresConectados; }
    public String getModoDeJuego() { return modoDeJuego; }
    public void setModoDeJuego(String modoDeJuego) { this.modoDeJuego = modoDeJuego; }
}