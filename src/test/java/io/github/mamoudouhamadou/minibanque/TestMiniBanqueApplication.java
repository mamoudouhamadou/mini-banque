package io.github.mamoudouhamadou.minibanque;

import org.springframework.boot.SpringApplication;

public class TestMiniBanqueApplication {

	public static void main(String[] args) {
		SpringApplication.from(MiniBanqueApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
