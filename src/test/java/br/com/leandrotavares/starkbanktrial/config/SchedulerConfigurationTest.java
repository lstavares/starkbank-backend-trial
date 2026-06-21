package br.com.leandrotavares.starkbanktrial.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import br.com.leandrotavares.starkbanktrial.application.invoice.InvoiceIssuingScheduler;

class SchedulerConfigurationTest {

    @Test
    void shouldEnableSchedulingAndExposeDefaultProperties() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(InvoiceIssuingConfig.class)) {
            assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class))
                    .isNotEmpty();

            InvoiceIssuingProperties properties = context.getBean(InvoiceIssuingProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getIntervalHours()).isEqualTo(3);
            assertThat(properties.getMaxBatches()).isEqualTo(8);
        }
    }

    @Test
    void shouldScheduleInvoiceIssuingEveryConfiguredNumberOfHours() throws NoSuchMethodException {
        Method issueInvoices = InvoiceIssuingScheduler.class.getDeclaredMethod("issueInvoices");

        Scheduled scheduled = issueInvoices.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedRateString()).isEqualTo("${invoice.scheduler.interval-hours:3}");
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.HOURS);
    }
}
