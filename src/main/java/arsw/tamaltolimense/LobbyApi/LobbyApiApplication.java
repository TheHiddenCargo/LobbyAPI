package arsw.tamaltolimense.LobbyApi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LobbyApiApplication {

	public static void main(String[] args) {
		// Usar WEBSITES_PORT que es la variable específica de Azure
		String port = System.getenv("WEBSITES_PORT");
		if (port == null) {
			// Si no estamos en Azure, usar PORT o el valor predeterminado
			port = System.getenv("PORT");
			if (port == null) {
				port = "8080";
			}
		}
		System.setProperty("server.port", port);

		SpringApplication.run(LobbyApiApplication.class, args);
	}

}
