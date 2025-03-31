package arsw.tamaltolimense.LobbyApi.repository;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LobbyRepository extends MongoRepository<Lobby, String> {
    // Buscar lobby por su nombre
    Lobby findByNombre(String nombre);
}
