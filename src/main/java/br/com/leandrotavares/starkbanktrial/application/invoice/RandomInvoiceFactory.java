package br.com.leandrotavares.starkbanktrial.application.invoice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateInvoiceRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.InvoiceDescriptionRequest;

@Component
public class RandomInvoiceFactory {

    static final int MIN_INVOICE_COUNT = 8;
    static final int MAX_INVOICE_COUNT = 12;
    static final long MIN_AMOUNT = 500L;
    static final long MAX_AMOUNT = 5_000L;
    static final long IMMEDIATE_INVOICE_DUE_OFFSET_HOURS = 2L;
    static final long IMMEDIATE_INVOICE_EXPIRATION_SECONDS = 24 * 60 * 60L;
    private static final DateTimeFormatter STARKBANK_DUE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private static final List<String> NAMES = List.of(
            "Ada Lovelace",
            "Grace Hopper",
            "Katherine Johnson",
            "Dorothy Vaughan",
            "Mary Jackson",
            "Alan Turing",
            "Tim Berners Lee",
            "Margaret Hamilton",
            "Barbara Liskov",
            "Donald Knuth",
            "Frances Allen",
            "Edsger Dijkstra"
    );

    public List<CreateInvoiceRequest> generate(String batchId, BatchTriggerSource triggerSource) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int invoiceCount = random.nextInt(MIN_INVOICE_COUNT, MAX_INVOICE_COUNT + 1);

        return random.ints(invoiceCount, 0, NAMES.size())
                .mapToObj(nameIndex -> createRequest(batchId, triggerSource, NAMES.get(nameIndex), random))
                .toList();
    }

    private CreateInvoiceRequest createRequest(
            String batchId,
            BatchTriggerSource triggerSource,
            String name,
            ThreadLocalRandom random
    ) {
        String source = triggerSource.name().toLowerCase(Locale.ROOT);
        return new CreateInvoiceRequest(
                random.nextLong(MIN_AMOUNT, MAX_AMOUNT + 1),
                name,
                generateValidCpf(random),
                List.of("trial", "batch:%s".formatted(batchId), "source:%s".formatted(source)),
                List.of(new InvoiceDescriptionRequest("service", "Trial invoice")),
                immediateDue(),
                IMMEDIATE_INVOICE_EXPIRATION_SECONDS
        );
    }

    private static String immediateDue() {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(IMMEDIATE_INVOICE_DUE_OFFSET_HOURS)
                .truncatedTo(ChronoUnit.MICROS)
                .format(STARKBANK_DUE_FORMATTER);
    }

    private static String generateValidCpf(ThreadLocalRandom random) {
        int[] digits = new int[11];
        for (int index = 0; index < 9; index++) {
            digits[index] = random.nextInt(10);
        }
        if (firstNineDigitsAreEqual(digits)) {
            digits[8] = (digits[8] + 1) % 10;
        }

        digits[9] = calculateVerificationDigit(digits, 9, 10);
        digits[10] = calculateVerificationDigit(digits, 10, 11);

        StringBuilder cpf = new StringBuilder(11);
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    private static boolean firstNineDigitsAreEqual(int[] digits) {
        for (int index = 1; index < 9; index++) {
            if (digits[index] != digits[0]) {
                return false;
            }
        }
        return true;
    }

    private static int calculateVerificationDigit(int[] digits, int length, int initialWeight) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += digits[index] * (initialWeight - index);
        }

        int digit = (sum * 10) % 11;
        return digit == 10 ? 0 : digit;
    }
}
