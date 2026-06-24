package stark_bank.challenge.scheduler;

import com.starkbank.Invoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import stark_bank.challenge.gateway.StarkBankInvoiceGateway;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceScheduler {

    private static final String[] FIRST_NAMES = {
        "Ana", "Carlos", "Fernanda", "João", "Maria",
        "Pedro", "Juliana", "Ricardo", "Beatriz", "Marcos"
    };

    private static final String[] LAST_NAMES = {
        "Silva", "Santos", "Oliveira", "Souza", "Costa",
        "Ferreira", "Lima", "Carvalho", "Alves", "Rodrigues"
    };

    private final StarkBankInvoiceGateway invoiceGateway;
    private final InvoiceRecordRepository invoiceRecordRepository;

    private static final int MAX_RETRIES = 7;

    @org.springframework.beans.factory.annotation.Value("${scheduler.invoice.retry-delay-ms:10000}")
    private long retryDelayMs;

    @Scheduled(fixedRateString = "${scheduler.invoice.rate-ms:10800000}", initialDelay = 15000L)
    public void generateInvoices() {
        int count = ThreadLocalRandom.current().nextInt(8, 13);
        log.info("Gerando {} boletos...", count);

        List<Invoice> invoices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                invoices.add(buildInvoice());
            } catch (Exception e) {
                log.error("Erro ao construir boleto: {}", e.getMessage(), e);
            }
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<Invoice> created = invoiceGateway.create(invoices);
                Instant scheduledAt = Instant.now();
                created.forEach(inv -> invoiceRecordRepository.save(
                    InvoiceRecord.builder()
                        .starkInvoiceId(inv.id)
                        .amount(inv.amount != null ? inv.amount.longValue() : null)
                        .name(inv.name)
                        .taxId(inv.taxId)
                        .due(inv.due)
                        .scheduledAt(scheduledAt)
                        .status("PENDING")
                        .build()
                ));
                log.info("{} boletos criados e registrados.", created.size());
                return;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("Tentativa {}/{} falhou: {}. Retentando em {}ms...",
                            attempt, MAX_RETRIES, e.getMessage(), retryDelayMs);
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    log.error("Erro ao criar boletos após {} tentativas: {}", MAX_RETRIES, e.getMessage(), e);
                }
            }
        }
    }

    private Invoice buildInvoice() throws Exception {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String name = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)]
                + " " + LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
        long amount = rnd.nextLong(1_000, 100_001);
        String due = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
                .toString().substring(0, 19) + "+00:00";

        HashMap<String, Object> params = new HashMap<>();
        params.put("amount", amount);
        params.put("name", name);
        params.put("taxId", CpfGenerator.generate());
        params.put("due", due);
        return new Invoice(params);
    }
}
