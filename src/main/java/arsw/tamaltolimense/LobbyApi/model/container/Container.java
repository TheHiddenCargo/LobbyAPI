package arsw.tamaltolimense.LobbyApi.model.container;


import lombok.Getter;

import java.util.Random;


public abstract class Container {
    @Getter private String id;
    @Getter protected int value;
    @Getter protected long limitTime;
    protected static final Random random = new Random();

    protected Container(String id, int value, long limitTime) {
        this.id = id;
        this.value = value;
        this.limitTime = limitTime;
    }
}
