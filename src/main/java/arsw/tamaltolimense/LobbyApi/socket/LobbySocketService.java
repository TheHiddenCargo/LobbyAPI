package arsw.tamaltolimense.LobbyApi.socket;

import arsw.tamaltolimense.LobbyApi.controller.LobbyController;
import arsw.tamaltolimense.LobbyApi.model.Lobby;
import arsw.tamaltolimense.LobbyApi.repository.LobbyRepository;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LobbySocketService {
    private static final Logger logger = LoggerFactory.getLogger(LobbySocketService.class);

    private SocketIOServer server;
    private final Map<String, String> sessionToNickname = new HashMap<>();
    private final Map<String, String> sessionToLobby = new HashMap<>();

    // Mapas para gestionar partidas
    private final Map<String, GameState> activeGames = new HashMap<>();
    private final Map<String, List<PlayerState>> gamePlayers = new HashMap<>();
    private final Map<String, Queue<ContainerInfo>> gameContainers = new HashMap<>();
    private final Map<String, Timer> gameTimers = new HashMap<>();

    // URL base del servicio de apuestas
    private static final String BID_SERVICE_URL = "https://thehiddencargo1.azure-api.net/bids/";

    // RestTemplate para comunicación con el servicio de apuestas
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    @Lazy
    private LobbyController lobbyController;

    @PostConstruct
    public void init() {
        try {
            Configuration config = new Configuration();
            config.setHostname("0.0.0.0");
            config.setPort(443);

            // Configuración correcta para Socket.IO
            config.setContext("/socket.io");
            config.setOrigin("*");
            config.setAllowCustomRequests(true);
            config.setAuthorizationListener(data -> true);
            config.setTransports(new Transport[]{Transport.WEBSOCKET, Transport.POLLING});

            // Configuraciones adicionales
            config.setPingTimeout(60000);
            config.setPingInterval(25000);

            logger.info("Creando instancia de SocketIOServer");
            server = new SocketIOServer(config);

            // Configurar listeners para eventos de conexión y desconexión
            server.addConnectListener(onConnected());
            server.addDisconnectListener(onDisconnected());

            // Configurar listeners para eventos específicos del lobby
            server.addEventListener("joinLobby", JoinLobbyData.class, onJoinLobby());
            server.addEventListener("leaveLobby", LeaveLobbyData.class, onLeaveLobby());
            server.addEventListener("playerReady", PlayerReadyData.class, onPlayerReady());
            server.addEventListener("playerNotReady", PlayerNotReadyData.class, onPlayerNotReady());
            server.addEventListener("chatMessage", ChatMessageData.class, onChatMessage());

            // Eventos del juego
            server.addEventListener("startGame", StartGameData.class, onStartGame());
            server.addEventListener("placeBid", PlaceBidData.class, onPlaceBid());
            server.addEventListener("leaveGame", LeaveGameData.class, onLeaveGame());

            logger.info("Iniciando servidor Socket.IO en puerto 443 con path /socket.io");
            server.start();
            logger.info("SocketIO Server iniciado en puerto 443 con path /socket.io");
        } catch (Exception e) {
            logger.error("Error al iniciar SocketIO Server", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (server != null) {
            logger.info("Deteniendo SocketIO Server");
            server.stop();
        }
    }

    private ConnectListener onConnected() {
        return client -> {
            logger.info("Cliente conectado: {}", client.getSessionId());
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String sessionId = client.getSessionId().toString();
            String nickname = sessionToNickname.get(sessionId);
            String lobbyName = sessionToLobby.get(sessionId);

            logger.info("Cliente desconectado: {}. Nickname: {}, Lobby: {}",
                    sessionId, nickname, lobbyName);

            if (nickname != null && lobbyName != null) {
                // Obtener el estado actual del lobby
                ResponseEntity<Lobby> response = lobbyController.obtenerLobby(lobbyName);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    // Actualizar el contador de jugadores conectados
                    Lobby lobby = response.getBody();
                    lobby.setJugadoresConectados(lobby.getJugadoresConectados() - 1);

                    // Guardar los cambios usando el repositorio
                    lobbyRepository.save(lobby);

                    // Notificar a todos en la sala que el jugador se desconectó
                    server.getRoomOperations(lobbyName).sendEvent("playerLeft", new PlayerLeftData(nickname));
                    logger.info("Jugador {} removido del lobby {} por desconexión", nickname, lobbyName);

                    // Si hay una partida activa, manejar la salida del jugador
                    if (activeGames.containsKey(lobbyName)) {
                        handlePlayerLeaveGame(lobbyName, nickname);
                    }
                }
            }

            // Limpiar los mapas
            sessionToNickname.remove(sessionId);
            sessionToLobby.remove(sessionId);
        };
    }

    private DataListener<JoinLobbyData> onJoinLobby() {
        return (client, data, ackRequest) -> {
            logger.info("Jugador {} intenta unirse al lobby: {}", data.getNickname(), data.getLobbyName());

            // Guardar la información de sesión
            String sessionId = client.getSessionId().toString();
            sessionToNickname.put(sessionId, data.getNickname());
            sessionToLobby.put(sessionId, data.getLobbyName());

            // Usar el controlador para agregar el jugador
            ResponseEntity<Lobby> response = lobbyController.agregarJugador(data.getLobbyName(), data.getNickname());

            if (response.getStatusCode().is2xxSuccessful()) {
                // Unir al cliente a la sala
                client.joinRoom(data.getLobbyName());

                // Notificar a todos en la sala que un jugador se unió
                server.getRoomOperations(data.getLobbyName()).sendEvent("playerJoined",
                        new PlayerJoinedData(data.getNickname()));

                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("Te has unido al lobby: " + data.getLobbyName());
                }

                logger.info("Jugador {} unido exitosamente al lobby {}", data.getNickname(), data.getLobbyName());
            } else {
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("Error al unirse al lobby: " + data.getLobbyName());
                }
                // Limpiar información de sesión en caso de error
                sessionToNickname.remove(sessionId);
                sessionToLobby.remove(sessionId);
            }
        };
    }

    private DataListener<LeaveLobbyData> onLeaveLobby() {
        return (client, data, ackRequest) -> {
            String sessionId = client.getSessionId().toString();
            String nickname = sessionToNickname.get(sessionId);
            String lobbyName = data.getLobbyName();

            logger.info("Jugador {} intentando salir del lobby {}", nickname, lobbyName);

            if (nickname != null && lobbyName != null) {
                // Quitar al jugador de la sala
                client.leaveRoom(lobbyName);

                // Notificar a todos en la sala que el jugador se fue
                server.getRoomOperations(lobbyName).sendEvent("playerLeft",
                        new PlayerLeftData(nickname));

                // Usar el nuevo método del controlador para gestionar cuando un jugador abandona voluntariamente un lobby
                ResponseEntity<Lobby> response = lobbyController.quitarJugador(lobbyName, nickname);
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Jugador {} removido del lobby {}", nickname, lobbyName);
                }

                // Limpiar el mapa de lobby para esta sesión
                sessionToLobby.remove(sessionId);

                // Si hay una partida activa, manejar la salida del jugador
                if (activeGames.containsKey(lobbyName)) {
                    handlePlayerLeaveGame(lobbyName, nickname);
                }
            }

            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("Has abandonado el lobby: " + lobbyName);
            }
        };
    }

    // 1. Añade más logs en el método onPlayerReady del backend (LobbySocketService.java)

    private DataListener<PlayerReadyData> onPlayerReady() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            String nickname = data.getNickname();

            logger.info("Jugador {} marcándose como listo en lobby {}", nickname, lobbyName);

            // Validar los datos recibidos
            if (lobbyName == null || lobbyName.isEmpty()) {
                logger.error("Nombre de lobby vacío o nulo en evento playerReady");
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("Error: Nombre de lobby inválido");
                }
                return;
            }

            if (nickname == null || nickname.isEmpty()) {
                logger.error("Nickname vacío o nulo en evento playerReady");
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("Error: Nickname inválido");
                }
                return;
            }

            // Obtener el lobby actual para diagnóstico
            Lobby lobby = lobbyRepository.findByNombre(lobbyName);
            if (lobby == null) {
                logger.error("Lobby {} no encontrado al marcar jugador {} como listo", lobbyName, nickname);
                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("Error: Lobby no encontrado");
                }
                return;
            }

            logger.info("Estado actual del lobby {}: jugadores conectados={}, jugadores listos={}",
                    lobbyName, lobby.getJugadoresConectados(), lobby.getJugadoresListos());

            try {
                // Usar el controlador para marcar al jugador como listo
                ResponseEntity<Lobby> response = lobbyController.agregarJugadorListo(lobbyName);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Lobby lobbyActualizado = response.getBody();
                    logger.info("Jugador {} marcado como listo. Nuevo estado del lobby: jugadores listos={}",
                            nickname, lobbyActualizado.getJugadoresListos());

                    // Notificar a todos en la sala que el jugador está listo
                    server.getRoomOperations(lobbyName).sendEvent("playerReady",
                            new PlayerReadyData(nickname, lobbyName));

                    // Enviar confirmación al cliente
                    if (ackRequest.isAckRequested()) {
                        ackRequest.sendAckData("Te has marcado como listo");
                    }

                    // Comprobar si todos los jugadores están listos
                    if (lobbyActualizado.getJugadoresListos() == lobbyActualizado.getJugadoresConectados()) {
                        server.getRoomOperations(lobbyName).sendEvent("allPlayersReady", lobbyName);
                        logger.info("Todos los jugadores listos en lobby {}", lobbyName);
                    }
                } else {
                    logger.error("Error al marcar jugador {} como listo en lobby {}: {}",
                            nickname, lobbyName, response.getStatusCode());

                    if (ackRequest.isAckRequested()) {
                        ackRequest.sendAckData("Error al marcarte como listo");
                    }
                }
            } catch (Exception e) {
                logger.error("Excepción al marcar jugador {} como listo en lobby {}: {}",
                        nickname, lobbyName, e.getMessage(), e);

                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("Error: " + e.getMessage());
                }
            }
        };
    }

    private DataListener<PlayerNotReadyData> onPlayerNotReady() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            String nickname = data.getNickname();

            logger.info("Jugador {} marcándose como no listo en lobby {}", nickname, lobbyName);

            // Usar el controlador para marcar al jugador como no listo
            ResponseEntity<Lobby> response = lobbyController.quitarJugadorListo(lobbyName);

            if (response.getStatusCode().is2xxSuccessful()) {
                // Notificar a todos en la sala que el jugador no está listo
                server.getRoomOperations(lobbyName).sendEvent("playerNotReady",
                        new PlayerNotReadyData(nickname, lobbyName));
            }
        };
    }

    private DataListener<ChatMessageData> onChatMessage() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            String nickname = data.getNickname();
            String message = data.getMessage();

            logger.info("Mensaje recibido de {} en lobby {}: {}", nickname, lobbyName, message);

            // Reenviar el mensaje a todos en la sala
            server.getRoomOperations(lobbyName).sendEvent("chatMessage", data);
        };
    }

    // Nuevo método para manejar el inicio del juego
    private DataListener<StartGameData> onStartGame() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            logger.info("Solicitud para iniciar juego en lobby: {}", lobbyName);

            // Obtener el lobby actual usando el método correcto
            Optional<Lobby> optionalLobby = lobbyRepository.findOptionalByNombre(lobbyName);

            if (optionalLobby.isPresent()) {
                Lobby lobby = optionalLobby.get();

                // Verificar si todos los jugadores están listos
                if (lobby.getJugadoresListos() < 2 || lobby.getJugadoresListos() != lobby.getJugadoresConectados()) {
                    sendErrorToClient(client, "No se puede iniciar el juego. Se necesitan al menos 2 jugadores y todos deben estar listos.", ackRequest);
                    return;
                }

                // Inicializar estado del juego
                GameState gameState = new GameState();
                gameState.setLobbyName(lobbyName);
                gameState.setCurrentRound(1);

                // Usar el nombre de campo correcto según tu modelo
                int rounds = lobby.getNumeroDeRondas() > 0 ? lobby.getNumeroDeRondas() : 3;

                gameState.setTotalRounds(rounds);
                gameState.setStatus("STARTING");

                activeGames.put(lobbyName, gameState);

                // Inicializar jugadores - usar el método correcto según tu modelo
                List<PlayerState> players = new ArrayList<>();
                List<String> playersList = lobby.getJugadores();

                if (playersList != null && !playersList.isEmpty()) {
                    for (String playerName : playersList) {
                        PlayerState player = new PlayerState();
                        player.setNickname(playerName);
                        player.setBalance(1000); // Saldo inicial
                        player.setScore(0);
                        players.add(player);
                    }
                    gamePlayers.put(lobbyName, players);

                    // Generar contenedores para todas las rondas
                    Queue<ContainerInfo> containers = generateContainers(gameState.getTotalRounds() * 2);
                    gameContainers.put(lobbyName, containers);

                    // Iniciar la primera ronda
                    startNewRound(lobbyName);

                    // Notificar que el juego ha comenzado
                    server.getRoomOperations(lobbyName).sendEvent("gameStarted",
                            createGameStartedData(lobbyName, gameState, players));

                    logger.info("Juego iniciado en lobby: {}", lobbyName);
                } else {
                    sendErrorToClient(client, "No hay jugadores en el lobby: " + lobbyName, ackRequest);
                }
            } else {
                sendErrorToClient(client, "Lobby no encontrado: " + lobbyName, ackRequest);
            }
        };
    }

    // Método para crear el objeto de datos de inicio de juego
    private GameStartedData createGameStartedData(String lobbyName, GameState state, List<PlayerState> players) {
        GameStartedData data = new GameStartedData();

        // Obtener nombres de los jugadores
        List<String> playerNames = new ArrayList<>();
        for (PlayerState player : players) {
            playerNames.add(player.getNickname());
        }

        data.setPlayers(playerNames);
        data.setContainer(state.getCurrentContainer());
        data.setInitialBid(100); // Apuesta inicial predeterminada
        data.setRound(state.getCurrentRound());
        data.setTotalRounds(state.getTotalRounds());

        return data;
    }

    // Método para iniciar una nueva ronda
    private void startNewRound(String lobbyName) {
        if (!activeGames.containsKey(lobbyName)) {
            logger.warn("No se puede iniciar una nueva ronda. Juego no encontrado: {}", lobbyName);
            return;
        }


        GameState gameState = activeGames.get(lobbyName);
        Queue<ContainerInfo> containers = gameContainers.get(lobbyName);

        // Verificar si ya se llegó al límite de rondas
        if (gameState.getCurrentRound() > gameState.getTotalRounds()) {
            endGame(lobbyName);
            return;
        }

        // Obtener el siguiente contenedor
        ContainerInfo container = containers.poll();
        if (container == null) {
            logger.error("No hay más contenedores disponibles para el lobby {}", lobbyName);
            endGame(lobbyName);
            return;
        }

        // Actualizar el estado del juego
        gameState.setCurrentContainer(container);
        gameState.setStatus("BIDDING");
        gameState.setLastBidder(null);
        gameState.setCurrentBid(100); // Apuesta inicial

        try {
            // Crear la apuesta inicial en el servicio de apuestas
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("idContainer", container.getId());
            requestBody.put("initialValue", 100);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Ocp-Apim-Subscription-Key", "b553314cb92447a6bb13871a44b16726");

            String apiUrl = BID_SERVICE_URL + "/bids/start?idContainer=" + container.getId() +
                    "&initialValue=100";

            // Llamar al servicio de apuestas para iniciar la subasta
            HttpEntity<Object> requestEntity = new HttpEntity<>(headers);

            // Hacer la petición POST con parámetros de consulta
            logger.info("Iniciando subasta a {}", apiUrl);
            ResponseEntity<Object> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Object.class
            );

            logger.info("Respuesta del servicio de apuestas: {}", response.getStatusCode());

            // Enviar notificación de nueva ronda a todos los jugadores
            NewRoundData roundData = new NewRoundData();
            roundData.setRound(gameState.getCurrentRound());
            roundData.setTotalRounds(gameState.getTotalRounds());
            roundData.setContainer(container);
            roundData.setInitialBid(100);

            server.getRoomOperations(lobbyName).sendEvent("newRound", roundData);

            // Configurar un temporizador para finalizar la subasta después de un tiempo determinado
            setupAuctionTimer(lobbyName, 30); // 30 segundos por ronda

            logger.info("Nueva ronda iniciada en lobby {}: Ronda {}/{}",
                    lobbyName, gameState.getCurrentRound(), gameState.getTotalRounds());
        } catch (Exception e) {
            logger.error("Error al iniciar nueva ronda en lobby {}: {}", lobbyName, e.getMessage());
            endAuctionRound(lobbyName);
        }
    }

    // Método para configurar un temporizador para la subasta actual
    private void setupAuctionTimer(String lobbyName, int seconds) {
        // Cancelar cualquier temporizador existente
        if (gameTimers.containsKey(lobbyName)) {
            gameTimers.get(lobbyName).cancel();
        }

        // Crear un nuevo temporizador
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                endAuctionRound(lobbyName);
            }
        }, seconds * 1000);

        gameTimers.put(lobbyName, timer);

        // Notificar a los clientes sobre el tiempo restante
        server.getRoomOperations(lobbyName).sendEvent("auctionTimer", seconds);
    }

    // Método para manejar las apuestas de los jugadores
    private DataListener<PlaceBidData> onPlaceBid() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            String nickname = data.getNickname();
            int amount = data.getAmount();

            logger.info("Apuesta recibida de {} en lobby {}: ${}", nickname, lobbyName, amount);

            // Verificar si el juego existe
            if (!activeGames.containsKey(lobbyName)) {
                sendErrorToClient(client, "Juego no encontrado", ackRequest);
                return;
            }

            GameState gameState = activeGames.get(lobbyName);

            // Verificar que el juego esté en estado de apuestas
            if (!"BIDDING".equals(gameState.getStatus())) {
                sendErrorToClient(client, "No se pueden realizar apuestas en este momento", ackRequest);
                return;
            }

            // Verificar que el jugador exista
            PlayerState player = findPlayerByNickname(lobbyName, nickname);
            if (player == null) {
                sendErrorToClient(client, "Jugador no encontrado en el juego", ackRequest);
                return;
            }

            // Verificar saldo y monto de apuesta
            if (player.getBalance() < amount) {
                sendErrorToClient(client, "Saldo insuficiente para realizar esta apuesta", ackRequest);
                return;
            }

            if (amount <= gameState.getCurrentBid()) {
                sendErrorToClient(client, "La apuesta debe ser mayor que la apuesta actual", ackRequest);
                return;
            }

            try {
                // Enviar la apuesta al servicio de BidService
                ContainerInfo container = gameState.getCurrentContainer();

                // Crear el payload para el servicio de apuestas
                Map<String, Object> payload = new HashMap<>();
                payload.put("newOwner", nickname);
                payload.put("amount", amount);
                payload.put("limit", player.getBalance());

                // Configurar para comunicarse con el WebSocket del servicio de apuestas
                // Nota: Esto normalmente requeriría un cliente WebSocket específico
                // Como alternativa, usamos el endpoint REST

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Ocp-Apim-Subscription-Key", "b553314cb92447a6bb13871a44b16726");;

                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

                // Hacer la petición REST
                restTemplate.postForEntity(
                        BID_SERVICE_URL + "/bids/offer?container=" + container.getId() + "&owner=" + nickname + "&amount=" + amount,
                        requestEntity,
                        Object.class
                );

                // Actualizar el estado del juego
                gameState.setCurrentBid(amount);
                gameState.setLastBidder(nickname);

                // Enviar la nueva apuesta a todos los jugadores
                server.getRoomOperations(lobbyName).sendEvent("newBid",
                        new NewBidData(nickname, amount));

                // Reiniciar el temporizador para dar más tiempo
                setupAuctionTimer(lobbyName, 15); // 15 segundos adicionales después de una apuesta

                if (ackRequest.isAckRequested()) {
                    ackRequest.sendAckData("Apuesta realizada con éxito");
                }
            } catch (Exception e) {
                logger.error("Error al procesar apuesta de {} en lobby {}: {}",
                        nickname, lobbyName, e.getMessage());
                sendErrorToClient(client, "Error al procesar la apuesta: " + e.getMessage(), ackRequest);
            }
        };
    }

    // Método para finalizar una ronda de subasta
    private void endAuctionRound(String lobbyName) {
        if (!activeGames.containsKey(lobbyName)) {
            logger.warn("No se puede finalizar la ronda. Juego no encontrado: {}", lobbyName);
            return;
        }

        GameState gameState = activeGames.get(lobbyName);

        // Actualizar estado
        gameState.setStatus("REVEALING");

        // Cancelar cualquier temporizador activo
        if (gameTimers.containsKey(lobbyName)) {
            gameTimers.get(lobbyName).cancel();
            gameTimers.remove(lobbyName);
        }

        // Determinar el ganador de la ronda
        String winner = gameState.getLastBidder();
        ContainerInfo container = gameState.getCurrentContainer();
        int bidAmount = gameState.getCurrentBid();

        // Si nadie hizo una apuesta, pasar a la siguiente ronda
        if (winner == null) {
            logger.info("Nadie hizo una apuesta en lobby {}. Pasando a la siguiente ronda.", lobbyName);
            gameState.setCurrentRound(gameState.getCurrentRound() + 1);
            startNewRound(lobbyName);
            return;
        }

        // Calcular resultados
        int containerValue = container.getValue();
        int profit = containerValue - bidAmount;

        // Actualizar el saldo y puntuación del ganador
        PlayerState winnerPlayer = findPlayerByNickname(lobbyName, winner);
        if (winnerPlayer != null) {
            winnerPlayer.setBalance(winnerPlayer.getBalance() - bidAmount + containerValue);
            winnerPlayer.setScore(winnerPlayer.getScore() + profit);
        }

        // Enviar resultado a todos los jugadores
        BidResultData resultData = new BidResultData();
        resultData.setWinner(winner);
        resultData.setContainerId(container.getId());
        resultData.setContainerType(container.getType());
        resultData.setBidAmount(bidAmount);
        resultData.setContainerValue(containerValue);
        resultData.setProfit(profit);

        server.getRoomOperations(lobbyName).sendEvent("bidResult", resultData);

        // Revelar el contenedor
        server.getRoomOperations(lobbyName).sendEvent("containerRevealed", container);

        // Enviar actualizaciones del estado de los jugadores
        List<PlayerState> players = gamePlayers.get(lobbyName);
        for (PlayerState player : players) {
            PlayerUpdateData playerData = new PlayerUpdateData();
            playerData.setNickname(player.getNickname());
            playerData.setBalance(player.getBalance());
            playerData.setScore(player.getScore());

            server.getRoomOperations(lobbyName).sendEvent("playerUpdate", playerData);
        }

        logger.info("Subasta finalizada en lobby {}. Ganador: {}, Beneficio: ${}",
                lobbyName, winner, profit);

        // Pasar a la siguiente ronda después de un breve delay
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                gameState.setCurrentRound(gameState.getCurrentRound() + 1);
                startNewRound(lobbyName);
            }
        }, 5000); // 5 segundos de delay para mostrar resultados
    }

    // Método para manejar cuando un jugador abandona el juego
    private DataListener<LeaveGameData> onLeaveGame() {
        return (client, data, ackRequest) -> {
            String lobbyName = data.getLobbyName();
            String nickname = data.getNickname();

            logger.info("Jugador {} abandonando el juego en lobby {}", nickname, lobbyName);

            // Manejar la salida del jugador
            handlePlayerLeaveGame(lobbyName, nickname);

            if (ackRequest.isAckRequested()) {
                ackRequest.sendAckData("Has abandonado el juego");
            }
        };
    }

    // Método para manejar la salida de un jugador del juego
    private void handlePlayerLeaveGame(String lobbyName, String nickname) {
        if (!activeGames.containsKey(lobbyName)) {
            return;
        }

        // Notificar a los demás jugadores
        server.getRoomOperations(lobbyName).sendEvent("playerLeftGame", new PlayerLeftGameData(nickname));

        // Eliminar al jugador de la lista
        List<PlayerState> players = gamePlayers.get(lobbyName);
        if (players != null) {
            players.removeIf(p -> p.getNickname().equals(nickname));

            // Si quedan menos de 2 jugadores, finalizar el juego
            if (players.size() < 2) {
                logger.info("Quedan menos de 2 jugadores en lobby {}. Finalizando juego.", lobbyName);
                endGame(lobbyName);
            }
        }
    }

    // Método para finalizar el juego
    private void endGame(String lobbyName) {
        if (!activeGames.containsKey(lobbyName)) {
            return;
        }

        // Determinar ganador
        List<PlayerState> players = gamePlayers.get(lobbyName);
        if (players != null && !players.isEmpty()) {
            // Encontrar el jugador con mayor puntuación
            PlayerState winner = players.get(0);
            for (PlayerState player : players) {
                if (player.getScore() > winner.getScore()) {
                    winner = player;
                }
            }

            // Crear datos del resultado final
            GameEndData endData = new GameEndData();
            endData.setWinner(winner.getNickname());
            endData.setFinalScores(players);

            // Enviar resultado a todos los jugadores
            server.getRoomOperations(lobbyName).sendEvent("gameEnd", endData);
            logger.info("Juego finalizado en lobby {}. Ganador: {}", lobbyName, winner.getNickname());
        }

        // Cancelar cualquier temporizador activo
        if (gameTimers.containsKey(lobbyName)) {
            gameTimers.get(lobbyName).cancel();
            gameTimers.remove(lobbyName);
        }

        // Limpiar recursos
        activeGames.remove(lobbyName);
        gamePlayers.remove(lobbyName);
        gameContainers.remove(lobbyName);
    }

    // Métodos para enviar eventos desde el controlador
    public void notifyLobbyUpdated(String lobbyName, Lobby lobby) {
        server.getRoomOperations(lobbyName).sendEvent("lobbyUpdated", lobby);
        logger.info("Notificación de actualización enviada al lobby {}", lobbyName);
    }

    public void notifyGameStarted(String lobbyName) {
        server.getRoomOperations(lobbyName).sendEvent("gameStarted", lobbyName);
        logger.info("Notificación de inicio de juego enviada al lobby {}", lobbyName);
    }

    public void notifyRoundEnded(String lobbyName, int remainingRounds) {
        server.getRoomOperations(lobbyName).sendEvent("roundEnded",
                new RoundEndedData(lobbyName, remainingRounds));
        logger.info("Notificación de fin de ronda enviada al lobby {}. Rondas restantes: {}",
                lobbyName, remainingRounds);
    }

    public void notifyGameEnded(String lobbyName) {
        // Evento opcional que podría utilizarse al integrar con otros componentes
        server.getRoomOperations(lobbyName).sendEvent("gameEnded", lobbyName);
        logger.info("Notificación de fin de juego enviada al lobby {}", lobbyName);
    }

    // Método para generar contenedores para el juego
    private Queue<ContainerInfo> generateContainers(int count) {
        Queue<ContainerInfo> containers = new LinkedList<>();

        // Generar contenedores con distintos tipos y valores
        String[] types = {"Normal", "Raro", "Épico", "Legendario"};
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            ContainerInfo container = new ContainerInfo();
            container.setId("container-" + UUID.randomUUID().toString().substring(0, 8));

            // Asignar un tipo aleatorio
            String type = types[random.nextInt(types.length)];
            container.setType(type);

            // Asignar un valor base según su tipo
            int baseValue;
            switch (type) {
                case "Raro":
                    baseValue = 500 + random.nextInt(500);
                    break;
                case "Épico":
                    baseValue = 1000 + random.nextInt(1000);
                    break;
                case "Legendario":
                    baseValue = 2000 + random.nextInt(3000);
                    break;
                default: // Normal
                    baseValue = 200 + random.nextInt(300);
            }
            container.setValue(baseValue);

            containers.add(container);
        }

        return containers;
    }

    // Método auxiliar para encontrar un jugador por su nickname
    private PlayerState findPlayerByNickname(String lobbyName, String nickname) {
        List<PlayerState> players = gamePlayers.get(lobbyName);
        if (players != null) {
            for (PlayerState player : players) {
                if (player.getNickname().equals(nickname)) {
                    return player;
                }
            }
        }
        return null;
    }

    // Método auxiliar para enviar errores al cliente
    private void sendErrorToClient(SocketIOClient client, String errorMessage, AckRequest ackRequest) {
        logger.warn(errorMessage);
        if (ackRequest.isAckRequested()) {
            ackRequest.sendAckData("Error: " + errorMessage);
        }
    }
}

// Clases de datos para eventos del juego
class StartGameData {
    private String lobbyName;

    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class GameStartedData {
    private List<String> players;
    private ContainerInfo container;
    private int initialBid;
    private int round;
    private int totalRounds;

    public List<String> getPlayers() { return players; }
    public void setPlayers(List<String> players) { this.players = players; }
    public ContainerInfo getContainer() { return container; }
    public void setContainer(ContainerInfo container) { this.container = container; }
    public int getInitialBid() { return initialBid; }
    public void setInitialBid(int initialBid) { this.initialBid = initialBid; }
    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }
}

class PlaceBidData {
    private String nickname;
    private String lobbyName;
    private String containerId;
    private int amount;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}

class NewBidData {
    private String nickname;
    private int amount;

    public NewBidData() {}

    public NewBidData(String nickname, int amount) {
        this.nickname = nickname;
        this.amount = amount;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}

class BidResultData {
    private String winner;
    private String containerId;
    private String containerType;
    private int bidAmount;
    private int containerValue;
    private int profit;

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }
    public int getBidAmount() { return bidAmount; }
    public void setBidAmount(int bidAmount) { this.bidAmount = bidAmount; }
    public int getContainerValue() { return containerValue; }
    public void setContainerValue(int containerValue) { this.containerValue = containerValue; }
    public int getProfit() { return profit; }
    public void setProfit(int profit) { this.profit = profit; }
}

class NewRoundData {
    private int round;
    private int totalRounds;
    private ContainerInfo container;
    private int initialBid;

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }
    public ContainerInfo getContainer() { return container; }
    public void setContainer(ContainerInfo container) { this.container = container; }
    public int getInitialBid() { return initialBid; }
    public void setInitialBid(int initialBid) { this.initialBid = initialBid; }
}

class PlayerUpdateData {
    private String nickname;
    private int balance;
    private int score;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}

class LeaveGameData {
    private String nickname;
    private String lobbyName;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class PlayerLeftGameData {
    private String nickname;

    public PlayerLeftGameData() {}

    public PlayerLeftGameData(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}

class GameEndData {
    private String winner;
    private List<PlayerState> finalScores;

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public List<PlayerState> getFinalScores() { return finalScores; }
    public void setFinalScores(List<PlayerState> finalScores) { this.finalScores = finalScores; }
}

class GameState {
    private String lobbyName;
    private int currentRound;
    private int totalRounds;
    private String status; // STARTING, BIDDING, REVEALING, FINISHED, ERROR
    private ContainerInfo currentContainer;
    private int currentBid;
    private String lastBidder;

    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public ContainerInfo getCurrentContainer() { return currentContainer; }
    public void setCurrentContainer(ContainerInfo currentContainer) { this.currentContainer = currentContainer; }
    public int getCurrentBid() { return currentBid; }
    public void setCurrentBid(int currentBid) { this.currentBid = currentBid; }
    public String getLastBidder() { return lastBidder; }
    public void setLastBidder(String lastBidder) { this.lastBidder = lastBidder; }
}

class PlayerState {
    private String nickname;
    private int balance;
    private int score;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}

class ContainerInfo {
    private String id;
    private String type;
    private int value;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
}

// Clases de datos para los eventos
class JoinLobbyData {
    private String nickname;
    private String lobbyName;

    public JoinLobbyData() {}

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class LeaveLobbyData {
    private String lobbyName;

    public LeaveLobbyData() {}

    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class PlayerJoinedData {
    private String nickname;

    public PlayerJoinedData() {}

    public PlayerJoinedData(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}

class PlayerLeftData {
    private String nickname;

    public PlayerLeftData() {}

    public PlayerLeftData(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}

class PlayerReadyData {
    private String nickname;
    private String lobbyName;

    public PlayerReadyData() {}

    public PlayerReadyData(String nickname, String lobbyName) {
        this.nickname = nickname;
        this.lobbyName = lobbyName;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class PlayerNotReadyData {
    private String nickname;
    private String lobbyName;

    public PlayerNotReadyData() {}

    public PlayerNotReadyData(String nickname, String lobbyName) {
        this.nickname = nickname;
        this.lobbyName = lobbyName;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
}

class ChatMessageData {
    private String nickname;
    private String lobbyName;
    private String message;

    public ChatMessageData() {}

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

class RoundEndedData {
    private String lobbyName;
    private int remainingRounds;

    public RoundEndedData() {}

    public RoundEndedData(String lobbyName, int remainingRounds) {
        this.lobbyName = lobbyName;
        this.remainingRounds = remainingRounds;
    }

    public String getLobbyName() { return lobbyName; }
    public void setLobbyName(String lobbyName) { this.lobbyName = lobbyName; }
    public int getRemainingRounds() { return remainingRounds; }
    public void setRemainingRounds(int remainingRounds) { this.remainingRounds = remainingRounds; }
}