package arsw.tamaltolimense.LobbyApi.model.container;



public class RareContainer extends Container{


    public RareContainer(String id){
        super(id, 500 + random.nextInt(500),15000);

    }
}
