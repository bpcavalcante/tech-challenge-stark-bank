package stark_bank.challenge.gateway;

import com.starkbank.Project;
import com.starkbank.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StarkBankTransferGateway {

    private final Project project;

    public List<Transfer> create(List<Transfer> transfers) throws Exception {
        return Transfer.create(transfers, project);
    }
}
