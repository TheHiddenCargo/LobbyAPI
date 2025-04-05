package arsw.tamaltolimense.LobbyApi.service;


import arsw.tamaltolimense.LobbyApi.exception.LobbyException;
import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.model.Player;
import arsw.tamaltolimense.LobbyApi.model.container.*;
import arsw.tamaltolimense.LobbyApi.repository.LobbyRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class LobbyService {

   private static final Logger logger = LoggerFactory.getLogger(LobbyService.class);

   private LobbyRepository lobbyRepository;
   
   private static final String CONTAINER = "container-";

   private   Random random;

   @Autowired
   public LobbyService(LobbyRepository lobbyRepository) {
      this.lobbyRepository = lobbyRepository;
      this.random = new Random();
   }

   public Lobby getLobby(String nombre)  {
      return lobbyRepository.findByNombre(nombre);
   }

   public Lobby crearLobby(String nombre, String contrasena, int numeroRondas, int maxNumero,String modoJUego){
      Lobby nuevaLobby = new Lobby(nombre,contrasena, numeroRondas, maxNumero,modoJUego);
      logger.info("Lobby creado exitosamente: {}", nuevaLobby.getNombre());
      generateContainer(nuevaLobby);
      return lobbyRepository.save(nuevaLobby);
   }

   public void agregarUsuario(String nombreUsuario, Lobby lobby) throws LobbyException {
      lobby.agregarJugador(nombreUsuario);
      lobbyRepository.save(lobby);


   }

   public void desconectarUsuario(String nombreUsuario, Lobby lobby) {
      lobby.removerJugador(nombreUsuario);
      lobbyRepository.save(lobby);
   }

   public void usuarioListo(String usuario, Lobby lobby) throws LobbyException {
      lobby.cambiarEstadoJugador(usuario,true);
      lobbyRepository.save(lobby);
   }

   public void usuarioNoListo(String usuario, Lobby lobby) throws LobbyException {
      lobby.cambiarEstadoJugador(usuario,false);
      lobbyRepository.save(lobby);
   }

   public Container getContainer(Lobby lobby) {
      return lobby.getContainer();
   }

   public List<Player> getPlayers(Lobby lobby) {
      return lobby.getJugadores();
   }

   public boolean usuariosListos(Lobby lobby){
      return lobby.jugadoresListos();
   }

   public List<Lobby> getLobbies() {
      return lobbyRepository.findAll();
   }



   private void generateContainer(Lobby lobby){
      String[] types = {"Normal", "Raro", "Épico", "Legendario"};
      String type;

      Container container;
      for (int i = 0; i < lobby.getNumeroDeRondas(); i++) {
         type = types[random.nextInt(types.length)];
         
         switch (type) {
            case "Raro":
               container = new RareContainer(CONTAINER + UUID.randomUUID().toString().substring(0, 8));
               break;
            case "Épico":
               container = new EpicContainer(CONTAINER + UUID.randomUUID().toString().substring(0, 8));
               break;
            case "Legendario":
               container = new LegendaryContainer(CONTAINER + UUID.randomUUID().toString().substring(0, 8));
               break;
            default: // Normal
               container = new NormalContainer(CONTAINER + UUID.randomUUID().toString().substring(0, 8));
         }
         lobby.agregarContendor(container);
      }
   }
}
