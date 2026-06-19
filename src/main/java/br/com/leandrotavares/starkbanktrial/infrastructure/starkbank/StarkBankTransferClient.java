package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import java.util.List;

import org.springframework.stereotype.Component;

import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateTransferRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedTransferResult;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankIntegrationException;
import com.starkbank.Project;
import com.starkbank.Transfer;

@Component
public class StarkBankTransferClient {

    private final StarkBankProjectProvider projectProvider;
    private final StarkBankSdkGateway sdkGateway;

    public StarkBankTransferClient(StarkBankProjectProvider projectProvider, StarkBankSdkGateway sdkGateway) {
        this.projectProvider = projectProvider;
        this.sdkGateway = sdkGateway;
    }

    public CreatedTransferResult createTransfer(CreateTransferRequest request) {
        Project project = projectProvider.getProject();
        Transfer transfer = toSdkTransfer(request);

        try {
            return sdkGateway.createTransfers(List.of(transfer), project).stream()
                    .findFirst()
                    .map(StarkBankTransferClient::toCreatedTransferResult)
                    .orElseThrow(() -> new StarkBankIntegrationException(
                            "Stark Bank transfer creation returned no results.",
                            null
                    ));
        } catch (StarkBankIntegrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StarkBankIntegrationException("Unable to create Stark Bank transfer.", exception);
        }
    }

    private Transfer toSdkTransfer(CreateTransferRequest request) {
        Transfer transfer = new Transfer();
        transfer.amount = request.amount();
        transfer.bankCode = request.bankCode();
        transfer.branchCode = request.branchCode();
        transfer.accountNumber = request.accountNumber();
        transfer.accountType = request.accountType();
        transfer.taxId = request.taxId();
        transfer.name = request.name();
        transfer.externalId = request.externalId();
        transfer.tags = toStringArray(request.tags());
        return transfer;
    }

    private static CreatedTransferResult toCreatedTransferResult(Transfer transfer) {
        return new CreatedTransferResult(
                transfer.id,
                transfer.externalId,
                transfer.amount,
                toLong(transfer.fee),
                transfer.status
        );
    }

    private static String[] toStringArray(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.toArray(String[]::new);
    }

    private static Long toLong(Number value) {
        if (value == null) {
            return null;
        }
        return value.longValue();
    }
}
