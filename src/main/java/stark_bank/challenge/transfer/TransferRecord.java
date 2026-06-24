package stark_bank.challenge.transfer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "transfer_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRecord {

    @Id
    private String starkInvoiceId;

    private String starkTransferId;
    private Long netAmount;
    private Long fee;
    private String externalId;
    private Instant transferredAt;
}
