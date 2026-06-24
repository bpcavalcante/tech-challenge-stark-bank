package stark_bank.challenge.config;

import com.starkbank.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
@Profile("!test")
public class StarkBankConfig {

    @Value("${stark.project.id}")
    private String projectId;

    @Value("${stark.private.key.path:}")
    private String privateKeyPath;

    @Value("${stark.private.key:}")
    private String privateKeyContent;

    @Bean
    public Project starkBankProject() throws Exception {
        String key;
        if (!privateKeyPath.isBlank()) {
            key = Files.readString(Paths.get(privateKeyPath));
            log.info("Inicializando projeto Stark Bank via arquivo: {}", privateKeyPath);
        } else {
            key = privateKeyContent.replace("\\n", "\n");
            log.info("Inicializando projeto Stark Bank via variável de ambiente...");
        }
        return new Project("sandbox", projectId, key);
    }
}
