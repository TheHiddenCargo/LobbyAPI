package arsw.tamaltolimense.LobbyApi.model.container;

public class NormalContainer extends Container {

    public NormalContainer(String id) {
        super(id, 200 + random.nextInt(300),20000);
    }
}
