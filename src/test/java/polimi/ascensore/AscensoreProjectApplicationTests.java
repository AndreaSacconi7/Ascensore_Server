package polimi.ascensore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Real servlet container (the WebSocket config needs one) and an in-memory database instead of Supabase
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ascensore",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "supabase.jwks-url=http://localhost:1/jwks"
        })
class AscensoreProjectApplicationTests {

    @Test
    void contextLoads() {
    }

}
