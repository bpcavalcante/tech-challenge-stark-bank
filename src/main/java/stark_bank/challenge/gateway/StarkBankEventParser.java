package stark_bank.challenge.gateway;

import com.starkbank.Event;
import com.starkbank.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StarkBankEventParser {

    private final Project project;

    public Event parse(String body, String signature) throws Exception {
        return Event.parse(body, signature, project);
    }
}
