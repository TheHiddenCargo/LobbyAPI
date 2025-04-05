package arsw.tamaltolimense.LobbyApi.controller;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.repository.LobbyRepository;
import arsw.tamaltolimense.LobbyApi.service.LobbyService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lobbies")
@CrossOrigin(origins = "*") // Para desarrollo
public class LobbyController {
    private static final Logger logger = LoggerFactory.getLogger(LobbyController.class);

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private LobbyService lobbyService;



    @PostMapping
    public ResponseEntity<Lobby> crearLobby(@RequestBody Map<String,String> lobbyDatta) {
        String nombre = lobbyDatta.get("nombre");
        Lobby lobbyGuardada = lobbyService.getLobby(nombre);

        if(lobbyGuardada == null){
            String contrasena = lobbyDatta.get("contrasena");
            int numeroRondas = Integer.parseInt(lobbyDatta.get("numeroRondas"));
            int maximoParticipantes = Integer.parseInt(lobbyDatta.get("maximoParticipantes"));
            String modoJuego = lobbyDatta.get("modoJuego");
            lobbyGuardada = lobbyService.crearLobby(nombre,contrasena,numeroRondas,maximoParticipantes,modoJuego);
            logger.info("Creando nuevo lobby: {}", nombre);
        }

        return ResponseEntity.ok(lobbyGuardada);


    }
    @GetMapping("/listar")
    public ResponseEntity<List<Lobby>> listarLobbies() {
        logger.info("Listando todos los lobbies disponibles");
        return ResponseEntity.ok(lobbyService.getLobbies());

    }



    @GetMapping("/{nombre}")
    public ResponseEntity<Lobby> obtenerLobby(@PathVariable String nombre) {
        logger.info("Obteniendo información de lobby: {}", nombre);
        Lobby lobby = lobbyRepository.findByNombre(nombre);
        if (lobby == null) {
            logger.warn("Lobby no encontrado: {}", nombre);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lobby);
    }

    @DeleteMapping("/borrar")
    public  void borrarLobby() {
        lobbyRepository.deleteAll();
    }






}