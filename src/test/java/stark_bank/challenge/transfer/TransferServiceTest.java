package stark_bank.challenge.transfer;

import com.starkbank.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import stark_bank.challenge.gateway.StarkBankTransferGateway;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    StarkBankTransferGateway transferGateway;

    TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(transferGateway);
    }

    @Test
    void transfersNetAmountDiscountingFee() throws Exception {
        when(transferGateway.create(anyList())).thenReturn(List.of(Mockito.mock(Transfer.class)));

        service.transferNetAmount("inv-001", 10_000L, 150L);

        ArgumentCaptor<List<Transfer>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferGateway).create(captor.capture());
        assertThat(captor.getValue().get(0).amount).isEqualTo(9_850L);
    }

    @Test
    void doesNotTransferWhenNetAmountIsZero() throws Exception {
        service.transferNetAmount("inv-002", 100L, 100L);
        verify(transferGateway, never()).create(anyList());
    }

    @Test
    void doesNotTransferWhenFeeExceedsAmount() throws Exception {
        service.transferNetAmount("inv-003", 100L, 200L);
        verify(transferGateway, never()).create(anyList());
    }

    @Test
    void continuesWhenGatewayThrows() throws Exception {
        when(transferGateway.create(anyList())).thenThrow(new RuntimeException("SDK error"));
        service.transferNetAmount("inv-004", 10_000L, 100L);
    }

    @Test
    void transferUsesCorrectRecipientConstants() throws Exception {
        when(transferGateway.create(anyList())).thenReturn(List.of(Mockito.mock(Transfer.class)));

        service.transferNetAmount("inv-100", 10_000L, 0L);

        ArgumentCaptor<List<Transfer>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferGateway).create(captor.capture());

        Transfer transfer = captor.getValue().get(0);
        assertThat(transfer.bankCode).isEqualTo("20018183");
        assertThat(transfer.branchCode).isEqualTo("0001");
        assertThat(transfer.accountNumber).isEqualTo("6341320293482496");
        assertThat(transfer.accountType).isEqualTo("payment");
        assertThat(transfer.taxId).isEqualTo("20.018.183/0001-80");
        assertThat(transfer.name).isEqualTo("Stark Bank S.A.");
    }

    @Test
    void transferExternalIdContainsInvoiceId() throws Exception {
        when(transferGateway.create(anyList())).thenReturn(List.of(Mockito.mock(Transfer.class)));

        service.transferNetAmount("inv-200", 10_000L, 0L);

        ArgumentCaptor<List<Transfer>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferGateway).create(captor.capture());
        assertThat(captor.getValue().get(0).externalId).isEqualTo("invoice-inv-200");
    }

    @Test
    void transfersFullAmountWhenFeeIsZero() throws Exception {
        when(transferGateway.create(anyList())).thenReturn(List.of(Mockito.mock(Transfer.class)));

        service.transferNetAmount("inv-300", 10_000L, 0L);

        ArgumentCaptor<List<Transfer>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferGateway).create(captor.capture());
        assertThat(captor.getValue().get(0).amount).isEqualTo(10_000L);
    }
}
