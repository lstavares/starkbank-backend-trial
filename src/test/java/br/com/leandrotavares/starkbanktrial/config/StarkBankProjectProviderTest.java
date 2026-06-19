package br.com.leandrotavares.starkbanktrial.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankProjectProvider;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankConfigurationException;
import com.starkbank.Key;
import com.starkbank.Project;
import com.starkbank.Settings;

class StarkBankProjectProviderTest {

    @AfterEach
    void clearSdkGlobalUser() {
        Settings.user = null;
    }

    @Test
    void shouldLoadPrivateKeyFromPath(@TempDir Path tempDir) throws Exception {
        Key key = Key.create();
        Path privateKeyPath = tempDir.resolve("starkbank-test-key.pem");
        Files.writeString(privateKeyPath, key.privatePem);

        StarkBankProjectProvider provider = new StarkBankProjectProvider(properties(
                "sandbox",
                "123456789",
                privateKeyPath.toString(),
                null
        ));

        Project project = provider.getProject();

        assertThat(project.environment).isEqualTo("sandbox");
        assertThat(project.id).isEqualTo("123456789");
        assertThat(project.pem).isEqualTo(key.privatePem.trim());
        assertThat(Settings.user).isSameAs(project);
    }

    @Test
    void shouldLoadInlinePrivateKeyWithEscapedLineBreaks() {
        Key key = Key.create();
        StarkBankProjectProvider provider = new StarkBankProjectProvider(properties(
                "production",
                "project-inline",
                null,
                key.privatePem.replace("\n", "\\n")
        ));

        Project project = provider.getProject();

        assertThat(project.environment).isEqualTo("production");
        assertThat(project.id).isEqualTo("project-inline");
        assertThat(project.pem).isEqualTo(key.privatePem.trim());
        assertThat(Settings.user).isSameAs(project);
    }

    @Test
    void shouldPreferPrivateKeyPathOverInlinePrivateKey(@TempDir Path tempDir) throws Exception {
        Key pathKey = Key.create();
        Path privateKeyPath = tempDir.resolve("preferred-key.pem");
        Files.writeString(privateKeyPath, pathKey.privatePem);

        StarkBankProjectProvider provider = new StarkBankProjectProvider(properties(
                "sandbox",
                "project-path",
                privateKeyPath.toString(),
                "invalid-inline-key"
        ));

        Project project = provider.getProject();

        assertThat(project.pem).isEqualTo(pathKey.privatePem.trim());
    }

    @Test
    void shouldFailForInvalidEnvironmentOnFirstUse() {
        StarkBankProjectProvider provider = new StarkBankProjectProvider(properties(
                "staging",
                "project-id",
                null,
                Key.create().privatePem
        ));

        assertThatThrownBy(provider::getProject)
                .isInstanceOf(StarkBankConfigurationException.class)
                .hasMessage("STARKBANK_ENVIRONMENT must be either 'sandbox' or 'production'.");
    }

    @Test
    void shouldFailForMissingCredentialsOnFirstUse() {
        StarkBankProjectProvider provider = new StarkBankProjectProvider(properties(
                "sandbox",
                "",
                null,
                null
        ));

        assertThatThrownBy(provider::getProject)
                .isInstanceOf(StarkBankConfigurationException.class)
                .hasMessage("STARKBANK_PROJECT_ID must be configured before using Stark Bank.");
    }

    @Test
    void shouldFailForMissingPrivateKeyOnFirstUse() {
        StarkBankProjectProvider provider = new StarkBankProjectProvider(properties(
                "sandbox",
                "project-id",
                null,
                ""
        ));

        assertThatThrownBy(provider::getProject)
                .isInstanceOf(StarkBankConfigurationException.class)
                .hasMessage("STARKBANK_PRIVATE_KEY_PATH or STARKBANK_PRIVATE_KEY must be configured before using Stark Bank.");
    }

    @Test
    void shouldNotLeakInlinePrivateKeyInErrorMessage() {
        String secret = "secret-private-key-value";
        StarkBankProjectProvider provider = new StarkBankProjectProvider(properties(
                "sandbox",
                "project-id",
                null,
                secret
        ));

        assertThatThrownBy(provider::getProject)
                .isInstanceOf(StarkBankConfigurationException.class)
                .hasMessageNotContaining(secret);
    }

    private static StarkBankProperties properties(
            String environment,
            String projectId,
            String privateKeyPath,
            String privateKey
    ) {
        StarkBankProperties properties = new StarkBankProperties();
        properties.setEnvironment(environment);
        properties.setProjectId(projectId);
        properties.setPrivateKeyPath(privateKeyPath);
        properties.setPrivateKey(privateKey);
        return properties;
    }
}
