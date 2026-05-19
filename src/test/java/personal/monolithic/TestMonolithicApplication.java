package personal.monolithic;

import org.springframework.boot.SpringApplication;

public class TestMonolithicApplication {

	public static void main(String[] args) {
		SpringApplication.from(MonolithicApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
