package arsw.tamaltolimense.LobbyApi.repository;

import arsw.tamaltolimense.LobbyApi.model.Lobby;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface LobbyRepository extends MongoRepository<Lobby, String> {
    // Método original - devuelve un objeto Lobby directamente
    Lobby findByNombre(String nombre);

    // Método que devuelve un Optional<Lobby> (para compatibilidad con el código que usa findByName)
    @Query("{'nombre': ?0}")
    Optional<Lobby> findOptionalByNombre(String lobbyName);
}