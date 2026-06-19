package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.leandrotavares.starkbanktrial.config.StarkBankProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateTransferRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedTransferResult;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankIntegrationException;
import com.starkbank.Key;
import com.starkbank.Project;
import com.starkbank.Transfer;

class StarkBankTransferClientTest {

    @Test
    void shouldDelegateTransferCreationAndPreserveExternalId() throws Exception {
        Project project = project();
        FakeProjectProvider projectProvider = new FakeProjectProvider(project);
        FakeSdkGateway sdkGateway = new FakeSdkGateway();

        Transfer createdTransfer = new Transfer();
        createdTransfer.id = "trf_001";
        createdTransfer.externalId = "invoice-inv_001";
        createdTransfer.amount = 9_850L;
        createdTransfer.fee = 25;
        createdTransfer.status = "created";
        sdkGateway.createdTransfersResult = List.of(createdTransfer);

        StarkBankTransferClient client = new StarkBankTransferClient(projectProvider, sdkGateway);
        CreatedTransferResult result = client.createTransfer(new CreateTransferRequest(
                9_850L,
                "20018183",
                "0001",
                "00012345-6",
                "checking",
                "01234567890",
                "Ada Lovelace",
                "invoice-inv_001",
                List.of("invoice", "inv_001")
        ));

        Transfer sdkTransfer = (Transfer) sdkGateway.createdTransfersRequest.get(0);

        assertThat(projectProvider.calls).isEqualTo(1);
        assertThat(sdkGateway.createTransfersProject).isSameAs(project);
        assertThat(sdkTransfer.amount).isEqualTo(9_850L);
        assertThat(sdkTransfer.bankCode).isEqualTo("20018183");
        assertThat(sdkTransfer.branchCode).isEqualTo("0001");
        assertThat(sdkTransfer.accountNumber).isEqualTo("00012345-6");
        assertThat(sdkTransfer.accountType).isEqualTo("checking");
        assertThat(sdkTransfer.taxId).isEqualTo("01234567890");
        assertThat(sdkTransfer.name).isEqualTo("Ada Lovelace");
        assertThat(sdkTransfer.externalId).isEqualTo("invoice-inv_001");
        assertThat(sdkTransfer.tags).containsExactly("invoice", "inv_001");

        assertThat(result.id()).isEqualTo("trf_001");
        assertThat(result.externalId()).isEqualTo("invoice-inv_001");
        assertThat(result.amount()).isEqualTo(9_850L);
        assertThat(result.fee()).isEqualTo(25L);
        assertThat(result.status()).isEqualTo("created");
    }

    @Test
    void shouldTranslateSdkExceptionsWhenCreatingTransfer() throws Exception {
        FakeProjectProvider projectProvider = new FakeProjectProvider(project());
        FakeSdkGateway sdkGateway = new FakeSdkGateway();
        sdkGateway.createTransfersException = new Exception("sdk unavailable");

        StarkBankTransferClient client = new StarkBankTransferClient(projectProvider, sdkGateway);

        assertThatThrownBy(() -> client.createTransfer(new CreateTransferRequest(
                10_000L,
                "20018183",
                "0001",
                "00012345-6",
                "checking",
                "01234567890",
                "Ada Lovelace",
                "external-id",
                List.of("invoice")
        ))).isInstanceOf(StarkBankIntegrationException.class)
                .hasMessage("Unable to create Stark Bank transfer.");
    }

    private static Project project() throws Exception {
        return new Project("sandbox", "project-id", Key.create().privatePem);
    }

    private static class FakeProjectProvider extends StarkBankProjectProvider {

        private final Project project;
        private int calls;

        FakeProjectProvider(Project project) {
            super(new StarkBankProperties());
            this.project = project;
        }

        @Override
        public synchronized Project getProject() {
            calls++;
            return project;
        }
    }

    private static class FakeSdkGateway extends StarkBankSdkGateway {

        private List<?> createdTransfersRequest;
        private Project createTransfersProject;
        private List<Transfer> createdTransfersResult = List.of();
        private Exception createTransfersException;

        @Override
        public List<Transfer> createTransfers(List<?> transfers, Project project) throws Exception {
            createdTransfersRequest = transfers;
            createTransfersProject = project;
            if (createTransfersException != null) {
                throw createTransfersException;
            }
            return createdTransfersResult;
        }
    }
}
