package arsw.tamaltolimense.LobbyApi.controller;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.repository.LobbyRepository;
import arsw.tamaltolimense.LobbyApi.socket.SocketNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/lobbies")
public class LobbyController {

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private SocketNotifier socketNotifier;

    @PostMapping("/{nombre}/verificar")
    public ResponseEntity<String> verificarLobby(@PathVariable String nombre, @RequestBody Lobby lobbyInput) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        if (lobby.getContraseña().equals(lobbyInput.getContraseña())) {
            return ResponseEntity.ok("Contraseña correcta");
        } else {
            return ResponseEntity.status(401).body("Contraseña incorrecta");
        }
    }

    @GetMapping("/{nombre}/agregarListo")
    public ResponseEntity<Lobby> agregarJugadorListo(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        lobby.setJugadoresListos(lobby.getJugadoresListos() + 1);
        lobbyRepository.save(lobby);

        // Notificar a través del servicio de sockets
        socketNotifier.notifyLobbyUpdated(nombre, lobby);

        if (lobby.getJugadoresListos() == lobby.getJugadoresConectados()) {
            socketNotifier.notifyGameStarted(nombre);
        }
        return ResponseEntity.ok(lobby);
    }

    @GetMapping("/{nombre}/agregarConectado")
    public ResponseEntity<Lobby> agregarJugadorConectado(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        lobby.setJugadoresConectados(lobby.getJugadoresConectados() + 1);
        lobbyRepository.save(lobby);
        socketNotifier.notifyLobbyUpdated(nombre, lobby);
        return ResponseEntity.ok(lobby);
    }

    @PostMapping
    public ResponseEntity<Lobby> crearLobby(@RequestBody Lobby nuevaLobby) {
        if (lobbyRepository.findByNombre(nuevaLobby.getNombre()) != null) {
            return ResponseEntity.badRequest().body(null);
        }
        if (nuevaLobby.getJugadores() == null) {
            nuevaLobby.setJugadores(new ArrayList<>());
        }
        nuevaLobby.setJugadoresConectados(1);
        nuevaLobby.setJugadoresListos(0);
        Lobby lobbyGuardada = lobbyRepository.save(nuevaLobby);
        return ResponseEntity.ok(lobbyGuardada);
    }

    @PutMapping("/{nombre}/agregarJugador")
    public ResponseEntity<Lobby> agregarJugador(@PathVariable String nombre, @RequestParam String nickname) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        if (!lobby.getJugadores().contains(nickname)) {
            lobby.getJugadores().add(nickname);
            lobby.setJugadoresConectados(lobby.getJugadoresConectados() + 1);
            lobbyRepository.save(lobby);
            socketNotifier.notifyLobbyUpdated(nombre, lobby);
        }
        return ResponseEntity.ok(lobby);
    }

    @GetMapping("/{nombre}/quitarListo")
    public ResponseEntity<Lobby> quitarJugadorListo(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        if (lobby.getJugadoresListos() > 0) {
            lobby.setJugadoresListos(lobby.getJugadoresListos() - 1);
            lobbyRepository.save(lobby);
            socketNotifier.notifyLobbyUpdated(nombre, lobby);
        }
        return ResponseEntity.ok(lobby);
    }

    @GetMapping("/{nombre}")
    public ResponseEntity<Lobby> obtenerLobby(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lobby);
    }

    @GetMapping("/listar")
    public ResponseEntity<List<Lobby>> listarLobbies() {
        List<Lobby> lobbies = lobbyRepository.findAll();
        List<Lobby> lobbiesSinContraseña = lobbies.stream().map(lobby -> {
            Lobby lobbySinContraseña = new Lobby();
            lobbySinContraseña.setNombre(lobby.getNombre());
            lobbySinContraseña.setJugadores(lobby.getJugadores());
            lobbySinContraseña.setJugadoresConectados(lobby.getJugadoresConectados());
            lobbySinContraseña.setJugadoresListos(lobby.getJugadoresListos());
            lobbySinContraseña.setMaxJugadoresConectados(lobby.getMaxJugadoresConectados());
            lobbySinContraseña.setNumeroDeRondas(lobby.getNumeroDeRondas());
            lobbySinContraseña.setModoDeJuego(lobby.getModoDeJuego());
            return lobbySinContraseña;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(lobbiesSinContraseña);
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> borrarLobby(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        lobbyRepository.delete(lobby);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{nombre}/restarRonda")
    public ResponseEntity<Lobby> restarRonda(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        if (lobby.getNumeroDeRondas() > 0) {
            lobby.setNumeroDeRondas(lobby.getNumeroDeRondas() - 1);
            lobbyRepository.save(lobby);
            socketNotifier.notifyRoundEnded(nombre, lobby.getNumeroDeRondas());
        }
        return ResponseEntity.ok(lobby);
    }
}
