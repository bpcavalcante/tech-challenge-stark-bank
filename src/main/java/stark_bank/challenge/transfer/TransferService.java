package stark_bank.challenge.transfer;

import com.starkbank.Transfer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import stark_bank.challenge.gateway.StarkBankTransferGateway;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final String BANK_CODE      = "20018183";
    private static final String BRANCH_CODE    = "0001";
    private static final String ACCOUNT_NUMBER = "6341320293482496";
    private static final String ACCOUNT_TYPE   = "payment";
    private static final String TAX_ID         = "20.018.183/0001-80";
    private static final String NAME           = "Stark Bank S.A.";

    private final StarkBankTransferGateway transferGateway;

    public Optional<String> transferNetAmount(String invoiceId, long amount, long fee) {
        long netAmount = amount - fee;
        if (netAmount <= 0) {
            log.warn("Valor líquido inválido para invoice {}: {} cents. Transferência cancelada.", invoiceId, netAmount);
            return Optional.empty();
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put("amount", netAmount);
        params.put("bankCode", BANK_CODE);
        params.put("branchCode", BRANCH_CODE);
        params.put("accountNumber", ACCOUNT_NUMBER);
        params.put("accountType", ACCOUNT_TYPE);
        params.put("taxId", TAX_ID);
        params.put("name", NAME);
        params.put("externalId", "invoice-" + invoiceId);

        try {
            List<Transfer> created = transferGateway.create(List.of(new Transfer(params)));
            String transferId = created.get(0).id;
            log.info("Transferência criada: id={}, invoiceId={}, valor={} cents", transferId, invoiceId, netAmount);
            return Optional.ofNullable(transferId);
        } catch (Exception e) {
            log.error("Erro ao criar transferência para invoice {}: {}", invoiceId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
