package stark_bank.challenge.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecord, String> {
}
