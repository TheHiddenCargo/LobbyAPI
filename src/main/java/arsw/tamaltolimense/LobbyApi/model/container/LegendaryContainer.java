package arsw.tamaltolimense.LobbyApi.model.container;

public class LegendaryContainer extends Container {
    public LegendaryContainer(String id) {
        super(id,2000 + random.nextInt(3000),5000);
    }
}
