package arsw.tamaltolimense.LobbyApi.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de las lobbys")
                        .version("1.0")
                        .description("Microservicio que genera lobbys donde jugadores peuden unirse y queda guardada en la base de datos."));
    }
}