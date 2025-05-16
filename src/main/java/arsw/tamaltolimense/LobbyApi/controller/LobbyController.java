package arsw.tamaltolimense.LobbyApi.controller;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.repository.LobbyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/lobbies")
@CrossOrigin(origins = "*") // Para desarrollo
public class LobbyController {
    private static final Logger logger = LoggerFactory.getLogger(LobbyController.class);

    @Autowired
    private LobbyRepository lobbyRepository;



    @PostMapping("/{nombre}/verificar")
    public ResponseEntity<String> verificarLobby(@PathVariable String nombre, @RequestBody Lobby lobbyInput) {
        logger.info("Verificando contraseña para lobby");
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado");
            return ResponseEntity.notFound().build();
        }
        if (lobby.getContraseña().equals(lobbyInput.getContraseña())) {
            logger.info("Contraseña correcta para lobby");
            return ResponseEntity.ok("Contraseña correcta");
        } else {
            logger.warn("Contraseña incorrecta para lobby");
            return ResponseEntity.status(401).body("Contraseña incorrecta");
        }
    }
    @PutMapping("/{nombre}/quitarJugador")
    public ResponseEntity<Lobby> quitarJugador(@PathVariable String nombre, @RequestParam String nickname) {
        logger.info("Quitando jugador del lobby");
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado");
            return ResponseEntity.notFound().build();
        }

        // Quitar el jugador de la lista si existe
        if (lobby.getJugadores().contains(nickname)) {
            lobby.getJugadores().remove(nickname);

            // Decrementar jugadores conectados
            if (lobby.getJugadoresConectados() > 0) {
                lobby.setJugadoresConectados(lobby.getJugadoresConectados() - 1);
            }

            if (lobby.getJugadoresListos() > 0) {
                lobby.setJugadoresListos(lobby.getJugadoresListos() - 1);
            }

            // Verificar si la lista de jugadores está vacía
            if (lobby.getJugadores().isEmpty() || lobby.getJugadoresConectados() == 0) {
                // Si no hay jugadores, eliminar el lobby
                logger.info("Eliminando lobby porque no quedan jugadores");
                lobbyRepository.delete(lobby);

                // Notificar que el lobby ha sido eliminado (opcional)
                // Podrías crear un nuevo evento para esto si es necesario

                return ResponseEntity.noContent().build(); // 204 No Content
            } else {
                // Guardar los cambios
                lobbyRepository.save(lobby);

                // Notificar a través de Socket.I
                logger.info("Jugador quitado del lobby");
            }
        } else {
            logger.info("Jugador no encontrado en el lobby");
        }

        return ResponseEntity.ok(lobby);
    }

    @GetMapping("/{nombre}/agregarListo")
    public ResponseEntity<Lobby> agregarJugadorListo(@PathVariable String nombre) {
        try {

            // Buscar el lobby por nombre
            Lobby lobby = lobbyRepository.findByNombre(nombre);

            if (lobby == null) {
                logger.error("No se encontró el lobby con nombre");
                return ResponseEntity.notFound().build();
            }

            // Verificar que no exceda el número de jugadores conectados
            if (lobby.getJugadoresListos() >= lobby.getJugadoresConectados()) {
                logger.warn("El número de jugadores listos ya es igual al de conectados en lobby");
                return ResponseEntity.ok(lobby); // Devolver el lobby sin cambios
            }

            // Incrementar el contador de jugadores listos
            lobby.setJugadoresListos(lobby.getJugadoresListos() + 1);

            // Guardar los cambios
            lobby = lobbyRepository.save(lobby);

            logger.info("Jugador marcado como listo. Lobby actualizado:  jugadores listos de conectados");

            return ResponseEntity.ok(lobby);
        } catch (Exception e) {
            logger.error("Error al aumentar jugadores listos en lobby",e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{nombre}/agregarConectado")
    public ResponseEntity<Lobby> agregarJugadorConectado(@PathVariable String nombre) {
        logger.info("Agregando jugador conectado en lobby");
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado");
            return ResponseEntity.notFound().build();
        }
        lobby.setJugadoresConectados(lobby.getJugadoresConectados() + 1);
        lobbyRepository.save(lobby);


        return ResponseEntity.ok(lobby);
    }

    @PostMapping
    public ResponseEntity<Lobby> crearLobby(@RequestBody Lobby nuevaLobby) {
        logger.info("Creando nuevo lobby");
        if (lobbyRepository.findByNombre(nuevaLobby.getNombre()) != null) {
            logger.warn("El lobby {} ya existe", nuevaLobby.getNombre());
            return ResponseEntity.badRequest().body(null);
        }
        if (nuevaLobby.getJugadores() == null) {
            nuevaLobby.setJugadores(new ArrayList<>());
        }
        nuevaLobby.setJugadoresConectados(1);
        nuevaLobby.setJugadoresListos(1);
        Lobby lobbyGuardada = lobbyRepository.save(nuevaLobby);
        logger.info("Lobby creado exitosamente");
        return ResponseEntity.ok(lobbyGuardada);
    }

    @PutMapping("/{nombre}/agregarJugador")
    public ResponseEntity<Lobby> agregarJugador(@PathVariable String nombre, @RequestParam String nickname) {
        logger.info("Agregando jugador al lobby");
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado:");
            return ResponseEntity.notFound().build();
        }
        if (!lobby.getJugadores().contains(nickname)) {
            lobby.getJugadores().add(nickname);
            lobby.setJugadoresConectados(lobby.getJugadoresConectados() + 1);
            lobbyRepository.save(lobby);
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

        }
        return ResponseEntity.ok(lobby);
    }

    @GetMapping("/{nombre}")
    public ResponseEntity<Lobby> obtenerLobby(@PathVariable String nombre) {
        logger.info("Obteniendo información de lobby");
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado");
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lobby);
    }

    @GetMapping("/listar")
    public ResponseEntity<List<Lobby>> listarLobbies() {
        logger.info("Listando todos los lobbies disponibles");
        try {
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
        } catch (Exception e) {
            logger.error("Error al listar lobbies: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> borrarLobby(@PathVariable String nombre) {
        logger.info("Borrando lobby");
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado");
            return ResponseEntity.notFound().build();
        }
        lobbyRepository.delete(lobby);
        logger.info("Lobby borrado exitosamente");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{nombre}/restarRonda")
    public ResponseEntity<Lobby> restarRonda(@PathVariable String nombre) {
        logger.info("Restando ronda en lobby");
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado");
            return ResponseEntity.notFound().build();
        }
        if (lobby.getNumeroDeRondas() > 0) {
            lobby.setNumeroDeRondas(lobby.getNumeroDeRondas() - 1);
            lobbyRepository.save(lobby);

            logger.info("Ronda restada en lobby Rondas restantes");
        }
        return ResponseEntity.ok(lobby);
    }
}