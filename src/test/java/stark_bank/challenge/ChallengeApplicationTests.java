package stark_bank.challenge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import stark_bank.challenge.config.TestStarkBankConfig;
import stark_bank.challenge.scheduler.InvoiceScheduler;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestStarkBankConfig.class)
class ChallengeApplicationTests {

    @MockitoBean
    InvoiceScheduler invoiceScheduler;

    @Test
    void contextLoads() {
    }
}
