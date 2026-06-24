package stark_bank.challenge.gateway;

import com.starkbank.Invoice;
import com.starkbank.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StarkBankInvoiceGateway {

    private final Project project;

    public List<Invoice> create(List<Invoice> invoices) throws Exception {
        return Invoice.create(invoices, project);
    }
}
