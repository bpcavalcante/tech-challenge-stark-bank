package stark_bank.challenge.webhook;

import com.starkbank.Event;
import com.starkbank.Invoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import stark_bank.challenge.gateway.StarkBankEventParser;
import stark_bank.challenge.scheduler.InvoiceRecordRepository;
import stark_bank.challenge.transfer.TransferRecord;
import stark_bank.challenge.transfer.TransferRecordRepository;
import stark_bank.challenge.transfer.TransferService;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final StarkBankEventParser eventParser;
    private final TransferService transferService;
    private final InvoiceRecordRepository invoiceRecordRepository;
    private final TransferRecordRepository transferRecordRepository;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String body,
            @RequestHeader("Digital-Signature") String signature) {
        try {
            Event event = eventParser.parse(body, signature);
            log.info("Webhook recebido: subscription={}", event.subscription);

            if ("invoice".equals(event.subscription)) {
                if (event instanceof Event.InvoiceEvent invoiceEvent) {
                    Invoice.Log invoiceLog = invoiceEvent.log;
                    log.info("Invoice log type={}, invoiceId={}", invoiceLog.type,
                            invoiceLog.invoice != null ? invoiceLog.invoice.id : "null");
                    if ("credited".equals(invoiceLog.type)) {
                        processCredited(invoiceLog.invoice);
                    }
                } else {
                    log.warn("Evento invoice não reconhecido como InvoiceEvent. Tipo real: {}", event.getClass().getName());
                }
            }
        } catch (Exception e) {
            log.error("Erro ao processar webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }

    private void processCredited(Invoice invoice) {
        if (transferRecordRepository.existsById(invoice.id)) {
            log.warn("Webhook duplicado ignorado: invoiceId={}", invoice.id);
            return;
        }

        invoiceRecordRepository.findById(invoice.id).ifPresent(record -> {
            record.setStatus("PAID");
            invoiceRecordRepository.save(record);
        });

        long amount = invoice.amount.longValue();
        long fee = invoice.fee != null ? invoice.fee.longValue() : 0L;
        log.info("Invoice creditada: id={}, amount={}, fee={}", invoice.id, amount, fee);

        transferService.transferNetAmount(invoice.id, amount, fee).ifPresent(transferId -> {
            transferRecordRepository.save(TransferRecord.builder()
                    .starkInvoiceId(invoice.id)
                    .starkTransferId(transferId)
                    .netAmount(amount - fee)
                    .fee(fee)
                    .externalId("invoice-" + invoice.id)
                    .transferredAt(Instant.now())
                    .build());
            log.info("Transferência registrada: invoiceId={}, transferId={}", invoice.id, transferId);
        });
    }
}
