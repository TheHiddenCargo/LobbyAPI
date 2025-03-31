package arsw.tamaltolimense.LobbyApi.controller;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.repository.LobbyRepository;
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

    @GetMapping("/{nombre}/agregarListo")
    public ResponseEntity<Lobby> agregarJugadorListo(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        lobby.setJugadoresListos(lobby.getJugadoresListos() + 1);
        lobbyRepository.save(lobby);
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

    // Endpoint para agregar un jugador a la lobby
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
        }
        return ResponseEntity.ok(lobby);
    }

    // Endpoint para restar un jugador de los listos
    @GetMapping("/{nombre}/quitarListo")
    public ResponseEntity<Lobby> quitarJugadorListo(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        if (lobby.getJugadoresListos() > 0) {
            lobby.setJugadoresListos(lobby.getJugadoresListos() - 1);
            lobbyRepository.save(lobby);
        }
        return ResponseEntity.ok(lobby);
    }

    // Endpoint para obtener toda la información de una lobby
    @GetMapping("/{nombre}")
    public ResponseEntity<Lobby> obtenerLobby(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lobby);
    }

    // Endpoint para obtener todas las lobbies sin la contraseña
    @GetMapping("/listar")
    public ResponseEntity<List<Lobby>> listarLobbies() {
        List<Lobby> lobbies = lobbyRepository.findAll();
        List<Lobby> lobbiesSinContraseña = lobbies.stream().map(lobby -> {
            Lobby lobbySinContraseña = new Lobby();
            lobbySinContraseña.setNombre(lobby.getNombre());
            lobbySinContraseña.setJugadores(lobby.getJugadores());
            lobbySinContraseña.setJugadoresConectados(lobby.getJugadoresConectados());
            lobbySinContraseña.setJugadoresListos(lobby.getJugadoresListos());
            return lobbySinContraseña;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(lobbiesSinContraseña);
    }

    // Endpoint para borrar una lobby por su nombre
    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> borrarLobby(@PathVariable String nombre) {
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        lobbyRepository.delete(lobby);
        return ResponseEntity.noContent().build();
    }
}
