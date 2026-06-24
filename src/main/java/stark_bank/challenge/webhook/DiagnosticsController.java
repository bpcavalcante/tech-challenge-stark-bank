package stark_bank.challenge.webhook;

import com.starkbank.Event;
import com.starkbank.Invoice;
import com.starkbank.Project;
import com.starkbank.Webhook;
import com.starkbank.Key;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/diagnostics")
@RequiredArgsConstructor
@Profile("!prod")
public class DiagnosticsController {

    private final Project project;

    @GetMapping("/invoices")
    public List<Map<String, Object>> listInvoices() throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("limit", 20);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Invoice inv : Invoice.query(params, project)) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", inv.id);
            row.put("status", inv.status);
            row.put("amount", inv.amount);
            row.put("fee", inv.fee);
            row.put("due", inv.due);
            row.put("name", inv.name);
            row.put("taxId", inv.taxId);
            result.add(row);
        }
        return result;
    }

    @PostMapping("/invoices/{id}/pay")
    public Map<String, Object> simulatePayment(@PathVariable String id) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("status", "paid");
        Invoice updated = Invoice.update(id, params, project);
        Map<String, Object> result = new HashMap<>();
        result.put("id", updated.id);
        result.put("status", updated.status);
        result.put("fee", updated.fee);
        result.put("amount", updated.amount);
        return result;
    }

    @GetMapping("/events")
    public List<Map<String, Object>> listEvents() throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("limit", 30);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Event event : Event.query(params, project)) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", event.id);
            row.put("subscription", event.subscription);
            row.put("isDelivered", event.isDelivered);
            row.put("created", event.created);
            if (event instanceof Event.InvoiceEvent invoiceEvent && invoiceEvent.log != null) {
                row.put("logType", invoiceEvent.log.type);
                if (invoiceEvent.log.invoice != null) {
                    row.put("invoiceId", invoiceEvent.log.invoice.id);
                    row.put("invoiceStatus", invoiceEvent.log.invoice.status);
                    row.put("invoiceFee", invoiceEvent.log.invoice.fee);
                }
            }
            result.add(row);
        }
        return result;
    }

    @GetMapping("/webhooks")
    public List<Map<String, Object>> listWebhooks() throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Webhook wh : Webhook.query(new HashMap<>(), project)) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", wh.id);
            row.put("url", wh.url);
            row.put("subscriptions", wh.subscriptions);
            result.add(row);
        }
        return result;
    }

    @PostMapping("/generate-keys")
    public Map<String, Object> generateKeys() throws Exception {
        Key key = Key.create("keys/");
        Map<String, Object> result = new HashMap<>();
        result.put("publicPem", key.publicPem);
        result.put("keysDirectory", "keys/");
        result.put("instructions", "Copie o publicPem e registre no dashboard do Stark Bank. Reinicie a aplicação após configurar o novo STARK_PROJECT_ID.");
        return result;
    }

    @PostMapping("/invoices/test-create")
    public Map<String, Object> testCreateInvoice() throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("amount", 1000L);
        params.put("name", "Teste Diagnostico");
        params.put("taxId", "012.345.678-90");
        params.put("due", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1)
                .toString().substring(0, 19) + "+00:00");
        Invoice created = Invoice.create(List.of(new Invoice(params)), project).get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("id", created.id);
        result.put("status", created.status);
        result.put("amount", created.amount);
        return result;
    }
}
