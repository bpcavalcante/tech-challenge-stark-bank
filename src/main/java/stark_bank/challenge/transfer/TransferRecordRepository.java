package stark_bank.challenge.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRecordRepository extends JpaRepository<TransferRecord, String> {
}
