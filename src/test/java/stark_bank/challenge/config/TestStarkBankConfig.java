package stark_bank.challenge.config;

import com.starkbank.Project;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestStarkBankConfig {

    @Bean
    public Project starkBankProject() {
        return Mockito.mock(Project.class);
    }
}
