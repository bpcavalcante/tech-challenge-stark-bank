package stark_bank.challenge.scheduler;

import com.starkbank.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import stark_bank.challenge.gateway.StarkBankInvoiceGateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSchedulerTest {

    @Mock
    StarkBankInvoiceGateway invoiceGateway;
    @Mock
    InvoiceRecordRepository invoiceRecordRepository;

    InvoiceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InvoiceScheduler(invoiceGateway, invoiceRecordRepository);
        ReflectionTestUtils.setField(scheduler, "retryDelayMs", 0L);
    }

    @RepeatedTest(10)
    void generatesBetween8And12Invoices() throws Exception {
        when(invoiceGateway.create(anyList())).thenAnswer(i -> i.getArgument(0));

        scheduler.generateInvoices();

        ArgumentCaptor<List<Invoice>> captor = ArgumentCaptor.forClass(List.class);
        verify(invoiceGateway).create(captor.capture());

        assertThat(captor.getValue().size()).isBetween(8, 12);
    }

    @Test
    void eachInvoiceHasRequiredFields() throws Exception {
        when(invoiceGateway.create(anyList())).thenAnswer(i -> i.getArgument(0));

        scheduler.generateInvoices();

        ArgumentCaptor<List<Invoice>> captor = ArgumentCaptor.forClass(List.class);
        verify(invoiceGateway).create(captor.capture());

        for (Invoice invoice : captor.getValue()) {
            assertThat(invoice.name).isNotBlank();
            assertThat(invoice.taxId).matches("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
        }
    }

    @RepeatedTest(20)
    void invoiceAmountIsWithinExpectedRange() throws Exception {
        when(invoiceGateway.create(anyList())).thenAnswer(i -> i.getArgument(0));

        scheduler.generateInvoices();

        ArgumentCaptor<List<Invoice>> captor = ArgumentCaptor.forClass(List.class);
        verify(invoiceGateway).create(captor.capture());

        for (Invoice invoice : captor.getValue()) {
            assertThat(invoice.amount.longValue()).isBetween(1_000L, 100_000L);
        }
    }

    @Test
    void invoiceDueDateIsInFuture() throws Exception {
        when(invoiceGateway.create(anyList())).thenAnswer(i -> i.getArgument(0));

        scheduler.generateInvoices();

        ArgumentCaptor<List<Invoice>> captor = ArgumentCaptor.forClass(List.class);
        verify(invoiceGateway).create(captor.capture());

        for (Invoice invoice : captor.getValue()) {
            assertThat(invoice.due).isNotBlank();
            assertThat(OffsetDateTime.parse(invoice.due)).isAfter(OffsetDateTime.now());
        }
    }

    @Test
    void retriesOnFirstFailureThenSucceeds() throws Exception {
        when(invoiceGateway.create(anyList()))
                .thenThrow(new RuntimeException("first attempt fails"))
                .thenAnswer(i -> i.getArgument(0));

        scheduler.generateInvoices();

        verify(invoiceGateway, times(2)).create(anyList());
    }

    @Test
    void exhaustsAllRetriesWithoutThrowing() throws Exception {
        when(invoiceGateway.create(anyList())).thenThrow(new RuntimeException("SDK error"));

        scheduler.generateInvoices();

        verify(invoiceGateway, times(7)).create(anyList());
    }
}
