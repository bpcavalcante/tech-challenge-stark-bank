package stark_bank.challenge.scheduler;

import java.util.concurrent.ThreadLocalRandom;

public class CpfGenerator {

    public static String generate() {
        int[] d = new int[9];
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) d[i] = rnd.nextInt(10);

        int d1 = checkDigit(d, 10);
        int d2 = checkDigitWithFirst(d, d1, 11);

        return String.format("%d%d%d.%d%d%d.%d%d%d-%d%d",
                d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d1, d2);
    }

    private static int checkDigit(int[] digits, int startWeight) {
        int sum = 0;
        for (int i = 0; i < digits.length; i++) sum += digits[i] * (startWeight - i);
        int rem = sum % 11;
        return rem < 2 ? 0 : 11 - rem;
    }

    private static int checkDigitWithFirst(int[] digits, int first, int startWeight) {
        int sum = first * 2;
        for (int i = 0; i < digits.length; i++) sum += digits[i] * (startWeight - i);
        int rem = sum % 11;
        return rem < 2 ? 0 : 11 - rem;
    }
}
