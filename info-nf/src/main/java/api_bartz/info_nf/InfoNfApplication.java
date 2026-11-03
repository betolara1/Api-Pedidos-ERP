package api_bartz.info_nf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // <-- ATIVA O AGENDAMENTO DE TAREFAS NO SPRING
public class InfoNfApplication {

	public static void main(String[] args) {
		SpringApplication.run(InfoNfApplication.class, args);
	}
}
