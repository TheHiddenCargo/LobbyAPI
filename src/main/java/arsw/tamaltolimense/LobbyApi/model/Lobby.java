package arsw.tamaltolimense.LobbyApi.model;

import arsw.tamaltolimense.LobbyApi.exception.LobbyException;
import arsw.tamaltolimense.LobbyApi.model.container.Container;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


@Document(collection = "lobbies")

public class Lobby {
    @Id
    @Getter
    private String nombre;
    @Getter

    private String contrasena;
    @Getter
    private List<Player> jugadores;
    @Getter
    private AtomicInteger jugadoresConectados;
    @Getter
    private AtomicInteger jugadoresListos;

    @Getter
    private int numeroDeRondas;
    @Getter
    private int maxJugadoresConectados;
    @Getter
    private String modoDeJuego;
    @Getter
    private LinkedList<Container> containers;



    @Transient private final Object jugadoresLock = new Object();


    public Lobby() {}

    public Lobby(String nombre, String contrasena, int numeroDeRondas, int maxJugadoresConectados, String modoDeJuego) {
        this.nombre = nombre;
        this.contrasena = contrasena;
        this.jugadores = Collections.synchronizedList(new ArrayList<>());
        this.jugadoresConectados = new AtomicInteger(0);
        this.jugadoresListos = new AtomicInteger(0);
        this.numeroDeRondas = numeroDeRondas;
        this.maxJugadoresConectados = maxJugadoresConectados;
        this.modoDeJuego = modoDeJuego;
        this.containers = new LinkedList<>();

    }

    public void agregarJugador(String nombreJugador) throws LobbyException {

        synchronized (jugadoresLock) {
            Player jugador = new Player(nombreJugador);
            if(jugadores.contains(jugador)) throw new LobbyException(LobbyException.JUGADOR_CONECTADO);
            if(jugadores.size() == this.maxJugadoresConectados) throw new LobbyException("No se pueden conectar mas Jugadores");
            jugadores.add(jugador);
            jugadoresConectados.incrementAndGet();

        }
    }

    public void removerJugador(String nombreJugador) {

        synchronized (jugadoresLock) {
            for (Player jugador : jugadores) {
                if(jugador.getUsername().equals(nombreJugador)){
                    jugadores.remove(jugador);
                    this.jugadoresConectados.decrementAndGet();
                    break;
                }
            }

        }

    }

    public void cambiarEstadoJugador(String nombreJugador, boolean listo) throws LobbyException {
        boolean encontrado = false;
        for (Player jugador : jugadores) {
            if(jugador.getUsername().equals(nombreJugador)){
                encontrado = true;
                final boolean anteriorEstado = jugador.isListo();
                jugador.setListo(listo);
                if(anteriorEstado && !jugador.isListo()) this.jugadoresListos.decrementAndGet();
                if(!anteriorEstado && jugador.isListo()) this.jugadoresListos.incrementAndGet();
                break;
            }
        }
        if(!encontrado) throw new LobbyException(LobbyException.JUGADOR_NO_EN_LOBBY);
    }

    public boolean jugadoresListos(){
        return jugadoresListos.get() == maxJugadoresConectados;
    }

    public List<Container> agregarContendor(Container container){
        containers.add(container);
        return containers;
    }

    public Container getContainer(){
        return containers.poll();
    }


}