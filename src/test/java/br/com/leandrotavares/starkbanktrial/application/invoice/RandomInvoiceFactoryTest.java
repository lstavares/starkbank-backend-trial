package br.com.leandrotavares.starkbanktrial.application.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateInvoiceRequest;

class RandomInvoiceFactoryTest {

    private final RandomInvoiceFactory factory = new RandomInvoiceFactory();

    @Test
    void shouldGenerateRandomInvoicesWithinExpectedBounds() {
        for (int attempt = 0; attempt < 20; attempt++) {
            var requests = factory.generate("batch-001", BatchTriggerSource.MANUAL);

            assertThat(requests).hasSizeBetween(8, 12);
            assertThat(requests).allSatisfy(request -> {
                assertThat(request.amount()).isBetween(500L, 5_000L);
                assertThat(request.name()).isNotBlank();
                assertThat(request.taxId()).isNotBlank();
                assertThat(request.taxId()).hasSize(11);
                assertThat(isValidCpf(request.taxId())).isTrue();
                assertThat(request.tags()).contains("trial", "batch:batch-001", "source:manual");
                assertThat(request.due()).isNotBlank();
                OffsetDateTime due = OffsetDateTime.parse(request.due());
                OffsetDateTime now = OffsetDateTime.now();
                assertThat(due).isBetween(now.plusMinutes(110), now.plusMinutes(130));
                assertThat(request.expiration()).isEqualTo(86_400L);
                assertThat(request.descriptions())
                        .allSatisfy(description -> {
                            assertThat(description.key()).isNotBlank();
                            assertThat(description.value()).isNotBlank();
                            assertThat(description.value()).hasSizeLessThan(20);
                        })
                        .anySatisfy(description -> {
                            assertThat(description.key()).isEqualTo("service");
                            assertThat(description.value()).isEqualTo("Trial invoice");
                        });
            });
        }
    }

    @Test
    void shouldUseScheduledSourceTagWhenGeneratingScheduledInvoices() {
        CreateInvoiceRequest request = factory.generate("batch-002", BatchTriggerSource.SCHEDULED).get(0);

        assertThat(request.tags()).contains("source:scheduled");
    }

    private static boolean isValidCpf(String cpf) {
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }

        int firstDigit = verificationDigit(cpf, 9, 10);
        int secondDigit = verificationDigit(cpf, 10, 11);
        return Character.digit(cpf.charAt(9), 10) == firstDigit
                && Character.digit(cpf.charAt(10), 10) == secondDigit;
    }

    private static int verificationDigit(String cpf, int length, int initialWeight) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += Character.digit(cpf.charAt(index), 10) * (initialWeight - index);
        }

        int digit = (sum * 10) % 11;
        return digit == 10 ? 0 : digit;
    }
}
