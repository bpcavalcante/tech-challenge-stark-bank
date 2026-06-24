package stark_bank.challenge.scheduler;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "invoice_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRecord {

    @Id
    private String starkInvoiceId;

    private Long amount;
    private String name;
    private String taxId;
    private String due;
    private Instant scheduledAt;
    private String status;
}
