package arsw.tamaltolimense.LobbyApi.model;

import lombok.Getter;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;


@Document(collection = "players")
public class Player {
    @Id
    @Getter private String username;
    @Getter private boolean listo;

    @Transient private Object lock;

    public Player(String username) {
        this.username = username;
        this.listo = false;
        lock = new Object();
    }

    public void setListo(boolean listo) {
        synchronized (lock) {
            this.listo = listo;
        }

    }

    @Override
    public int hashCode(){
        return username.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        try{
            Player p = (Player) o;
            return this.username.equals(p.getUsername());
        }catch(ClassCastException e){
            return false;
        }


    }
}
