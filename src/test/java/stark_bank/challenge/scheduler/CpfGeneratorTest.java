package stark_bank.challenge.scheduler;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfGeneratorTest {

    @RepeatedTest(50)
    void generatedCpfMatchesFormat() {
        assertThat(CpfGenerator.generate()).matches("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    }

    @Test
    void generatedCpfHasCorrectLength() {
        assertThat(CpfGenerator.generate()).hasSize(14);
    }

    @RepeatedTest(50)
    void generatedCpfHasValidCheckDigits() {
        assertThat(isValidCpf(CpfGenerator.generate())).isTrue();
    }

    private boolean isValidCpf(String cpf) {
        String digits = cpf.replaceAll("[^0-9]", "");
        if (digits.length() != 11) return false;
        int[] d = new int[11];
        for (int i = 0; i < 11; i++) d[i] = digits.charAt(i) - '0';

        int sum = 0;
        for (int i = 0; i < 9; i++) sum += d[i] * (10 - i);
        int rem = sum % 11;
        int d1 = rem < 2 ? 0 : 11 - rem;
        if (d[9] != d1) return false;

        sum = d1 * 2;
        for (int i = 0; i < 9; i++) sum += d[i] * (11 - i);
        rem = sum % 11;
        int d2 = rem < 2 ? 0 : 11 - rem;
        return d[10] == d2;
    }
}
