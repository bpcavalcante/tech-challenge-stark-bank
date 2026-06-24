package stark_bank.challenge.webhook;

import com.starkbank.Event;
import com.starkbank.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import stark_bank.challenge.gateway.StarkBankEventParser;
import stark_bank.challenge.scheduler.InvoiceRecordRepository;
import stark_bank.challenge.transfer.TransferRecord;
import stark_bank.challenge.transfer.TransferRecordRepository;
import stark_bank.challenge.transfer.TransferService;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock StarkBankEventParser eventParser;
    @Mock TransferService transferService;
    @Mock InvoiceRecordRepository invoiceRecordRepository;
    @Mock TransferRecordRepository transferRecordRepository;

    WebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new WebhookController(eventParser, transferService, invoiceRecordRepository, transferRecordRepository);
    }

    @Test
    void returns200AndTransfersOnCreditedInvoice() throws Exception {
        Event event = buildInvoiceEvent("credited", "inv-123", 10_000L, 150);
        when(eventParser.parse(anyString(), anyString())).thenReturn(event);
        when(transferRecordRepository.existsById("inv-123")).thenReturn(false);
        when(invoiceRecordRepository.findById("inv-123")).thenReturn(Optional.empty());
        when(transferService.transferNetAmount("inv-123", 10_000L, 150L)).thenReturn(Optional.of("tr-abc"));

        ResponseEntity<Void> response = controller.receive("{}", "sig");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(transferService).transferNetAmount("inv-123", 10_000L, 150L);
        verify(transferRecordRepository).save(any(TransferRecord.class));
    }

    @Test
    void returns200ButDoesNotTransferOnNonCreditedEvent() throws Exception {
        Event event = buildInvoiceEvent("created", "inv-456", 5_000L, 0);
        when(eventParser.parse(anyString(), anyString())).thenReturn(event);

        ResponseEntity<Void> response = controller.receive("{}", "sig");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(transferService, never()).transferNetAmount(any(), any(Long.class), any(Long.class));
    }

    @Test
    void returns400OnInvalidSignature() throws Exception {
        when(eventParser.parse(anyString(), anyString())).thenThrow(new RuntimeException("Invalid signature"));

        ResponseEntity<Void> response = controller.receive("{}", "bad-sig");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(transferService, never()).transferNetAmount(any(), any(Long.class), any(Long.class));
    }

    @Test
    void returns200ForNonInvoiceSubscription() throws Exception {
        Event event = Mockito.mock(Event.class);
        setField(event, "subscription", "transfer");
        when(eventParser.parse(anyString(), anyString())).thenReturn(event);

        ResponseEntity<Void> response = controller.receive("{}", "sig");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(transferService, never()).transferNetAmount(any(), any(Long.class), any(Long.class));
    }

    @Test
    void returns200WhenInvoiceEventIsNotInvoiceEventInstance() throws Exception {
        Event event = Mockito.mock(Event.class);
        setField(event, "subscription", "invoice");
        when(eventParser.parse(anyString(), anyString())).thenReturn(event);

        ResponseEntity<Void> response = controller.receive("{}", "sig");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(transferService, never()).transferNetAmount(any(), any(Long.class), any(Long.class));
    }

    @Test
    void returns200AndTransfersWithZeroFeeWhenFeeIsNull() throws Exception {
        Event event = buildInvoiceEventWithNullFee("credited", "inv-789", 5_000L);
        when(eventParser.parse(anyString(), anyString())).thenReturn(event);
        when(transferRecordRepository.existsById("inv-789")).thenReturn(false);
        when(invoiceRecordRepository.findById("inv-789")).thenReturn(Optional.empty());
        when(transferService.transferNetAmount("inv-789", 5_000L, 0L)).thenReturn(Optional.of("tr-def"));

        ResponseEntity<Void> response = controller.receive("{}", "sig");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(transferService).transferNetAmount("inv-789", 5_000L, 0L);
    }

    @Test
    void ignoresDuplicateCreditedWebhook() throws Exception {
        Event event = buildInvoiceEvent("credited", "inv-dup", 10_000L, 100);
        when(eventParser.parse(anyString(), anyString())).thenReturn(event);
        when(transferRecordRepository.existsById("inv-dup")).thenReturn(true);

        ResponseEntity<Void> response = controller.receive("{}", "sig");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(transferRecordRepository, never()).save(any());
        verify(transferService, never()).transferNetAmount(any(), any(Long.class), any(Long.class));
    }

    @Test
    void doesNotSaveTransferRecordWhenTransferFails() throws Exception {
        Event event = buildInvoiceEvent("credited", "inv-fail", 10_000L, 100);
        when(eventParser.parse(anyString(), anyString())).thenReturn(event);
        when(transferRecordRepository.existsById("inv-fail")).thenReturn(false);
        when(invoiceRecordRepository.findById("inv-fail")).thenReturn(Optional.empty());
        when(transferService.transferNetAmount("inv-fail", 10_000L, 100L)).thenReturn(Optional.empty());

        ResponseEntity<Void> response = controller.receive("{}", "sig");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(transferRecordRepository, never()).save(any());
    }

    private Event buildInvoiceEvent(String type, String invoiceId, long amount, Integer fee) throws Exception {
        Invoice invoice = new Invoice(new HashMap<>());
        setField(invoice, "id", invoiceId);
        setField(invoice, "amount", (Number) amount);
        setField(invoice, "fee", fee);
        Invoice.Log invoiceLog = new Invoice.Log(null, type, null, invoice, null);
        return new Event.InvoiceEvent(invoiceLog, null, false, "invoice", null, null);
    }

    private Event buildInvoiceEventWithNullFee(String type, String invoiceId, long amount) throws Exception {
        Invoice invoice = new Invoice(new HashMap<>());
        setField(invoice, "id", invoiceId);
        setField(invoice, "amount", (Number) amount);
        setField(invoice, "fee", null);
        Invoice.Log invoiceLog = new Invoice.Log(null, type, null, invoice, null);
        return new Event.InvoiceEvent(invoiceLog, null, false, "invoice", null, null);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
