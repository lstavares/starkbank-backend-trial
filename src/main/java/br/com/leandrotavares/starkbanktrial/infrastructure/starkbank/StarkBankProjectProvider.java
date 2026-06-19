package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.leandrotavares.starkbanktrial.config.StarkBankProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankConfigurationException;
import com.starkbank.Project;
import com.starkbank.Settings;

@Component
public class StarkBankProjectProvider {

    private static final Set<String> VALID_ENVIRONMENTS = Set.of("sandbox", "production");

    private final StarkBankProperties properties;
    private Project project;

    public StarkBankProjectProvider(StarkBankProperties properties) {
        this.properties = properties;
    }

    public synchronized Project getProject() {
        if (project == null) {
            project = createProject();
        }

        Settings.user = project;
        return project;
    }

    private Project createProject() {
        String environment = normalizedEnvironment();
        String projectId = requireText(
                properties.getProjectId(),
                "STARKBANK_PROJECT_ID must be configured before using Stark Bank."
        );
        String privateKey = loadPrivateKey();

        try {
            return new Project(environment, projectId, privateKey);
        } catch (Exception exception) {
            throw new StarkBankConfigurationException(
                    "Unable to initialize Stark Bank project. Check environment, project id and private key.",
                    exception
            );
        }
    }

    private String normalizedEnvironment() {
        String environment = requireText(
                properties.getEnvironment(),
                "STARKBANK_ENVIRONMENT must be configured before using Stark Bank."
        ).toLowerCase(Locale.ROOT);

        if (!VALID_ENVIRONMENTS.contains(environment)) {
            throw new StarkBankConfigurationException(
                    "STARKBANK_ENVIRONMENT must be either 'sandbox' or 'production'."
            );
        }

        return environment;
    }

    private String loadPrivateKey() {
        if (hasText(properties.getPrivateKeyPath())) {
            return readPrivateKeyFromPath(properties.getPrivateKeyPath().trim());
        }

        if (hasText(properties.getPrivateKey())) {
            return properties.getPrivateKey().replace("\\n", "\n").trim();
        }

        throw new StarkBankConfigurationException(
                "STARKBANK_PRIVATE_KEY_PATH or STARKBANK_PRIVATE_KEY must be configured before using Stark Bank."
        );
    }

    private String readPrivateKeyFromPath(String privateKeyPath) {
        try {
            String privateKey = Files.readString(Path.of(privateKeyPath)).trim();
            if (!hasText(privateKey)) {
                throw new StarkBankConfigurationException("Stark Bank private key file is empty.");
            }
            return privateKey;
        } catch (IOException exception) {
            throw new StarkBankConfigurationException(
                    "Unable to read Stark Bank private key from STARKBANK_PRIVATE_KEY_PATH.",
                    exception
            );
        }
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new StarkBankConfigurationException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
