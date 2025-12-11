package shortly.mandmcorp.dev.shortly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class ShortlyApplication {
	public static void main(String[] args) {
		SpringApplication.run(ShortlyApplication.class, args);
	}

}
