package stark_bank.challenge.gateway;

import com.starkbank.Invoice;
import com.starkbank.Project;
import com.starkbank.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class StarkBankGatewayTest {

    @Mock
    Project project;

    StarkBankInvoiceGateway invoiceGateway;
    StarkBankTransferGateway transferGateway;
    StarkBankEventParser eventParser;

    @BeforeEach
    void setUp() {
        invoiceGateway = new StarkBankInvoiceGateway(project);
        transferGateway = new StarkBankTransferGateway(project);
        eventParser = new StarkBankEventParser(project);
    }

    @Test
    void invoiceGatewayPropagatesExceptionFromSdk() {
        assertThatThrownBy(() -> invoiceGateway.create(List.of(new Invoice(new java.util.HashMap<>()))))
                .isInstanceOf(Exception.class);
    }

    @Test
    void transferGatewayPropagatesExceptionFromSdk() {
        assertThatThrownBy(() -> transferGateway.create(List.of(new Transfer(new java.util.HashMap<>()))))
                .isInstanceOf(Exception.class);
    }

    @Test
    void eventParserPropagatesExceptionFromSdk() {
        assertThatThrownBy(() -> eventParser.parse("{}", "invalid-signature"))
                .isInstanceOf(Exception.class);
    }
}
