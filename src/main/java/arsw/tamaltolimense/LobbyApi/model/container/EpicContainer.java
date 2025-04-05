package arsw.tamaltolimense.LobbyApi.model.container;

public class EpicContainer extends Container {

    public EpicContainer(String id) {
        super(id, 1000 + random.nextInt(1000),10000);
    }
}
