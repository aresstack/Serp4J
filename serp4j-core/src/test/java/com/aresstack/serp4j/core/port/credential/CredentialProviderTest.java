package com.aresstack.serp4j.core.port.credential;

import com.aresstack.serp4j.core.model.id.ProviderId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CredentialProviderTest {

    private static final CredentialKey BRAVE_KEY = CredentialKey.of(ProviderId.of("brave"), "api_key");

    @Test
    void secretValueCopiesWipesAndNeverPrintsItsContent() {
        char[] source = "s3cret".toCharArray();
        SecretValue secret = SecretValue.of(source);
        source[0] = 'X';

        assertThat(secret.<String>use(String::new)).isEqualTo("s3cret");
        assertThat(secret.length()).isEqualTo(6);
        assertThat(secret.toString()).doesNotContain("s3cret");
        assertThat(secret).isEqualTo(SecretValue.of("s3cret")).isNotEqualTo(SecretValue.of("s3cret!"));

        char[] handedOut = secret.getChars();
        secret.close();
        assertThat(secret.isCleared()).isTrue();
        assertThat(handedOut).containsExactly("s3cret".toCharArray());
        assertThatIllegalStateException().isThrownBy(secret::getChars);
    }

    @Test
    void staticProviderReturnsFreshCopiesAndRequireFailsWithoutLeakingValues() {
        StaticCredentialProvider provider = StaticCredentialProvider.of(BRAVE_KEY, "abc");

        SecretValue first = provider.resolve(BRAVE_KEY).get();
        first.close();
        assertThat(provider.resolve(BRAVE_KEY).get().<String>use(String::new)).isEqualTo("abc");
        assertThat(provider.resolve(CredentialKey.of("missing"))).isEmpty();
        assertThatIllegalStateException()
                .isThrownBy(() -> provider.require(CredentialKey.of("missing")))
                .withMessageContaining("missing");

        provider.close();
        assertThat(provider.resolve(BRAVE_KEY)).isEmpty();
    }

    @Test
    void providersBuiltFromTheSameBuilderOwnTheirSecrets() {
        StaticCredentialProvider.Builder builder = StaticCredentialProvider.builder().put(BRAVE_KEY, "abc");
        StaticCredentialProvider first = builder.build();
        StaticCredentialProvider second = builder.build();

        first.close();

        assertThat(first.resolve(BRAVE_KEY)).isEmpty();
        assertThat(second.resolve(BRAVE_KEY).get().<String>use(String::new)).isEqualTo("abc");
        assertThat(builder.build().resolve(BRAVE_KEY).get().<String>use(String::new)).isEqualTo("abc");
    }

    @Test
    void environmentProviderMapsKeysToVariableNames() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("SERP4J_BRAVE_API_KEY", "from-env");
        env.put("SERP4J_EMPTY_KEY", "");
        EnvironmentCredentialProvider provider = EnvironmentCredentialProvider.of("SERP4J_", env::get);

        assertThat(provider.variableName(BRAVE_KEY)).isEqualTo("SERP4J_BRAVE_API_KEY");
        assertThat(provider.variableName(CredentialKey.of("brightdata-de.password")))
                .isEqualTo("SERP4J_BRIGHTDATA_DE_PASSWORD");
        assertThat(provider.resolve(BRAVE_KEY).get().<String>use(String::new)).isEqualTo("from-env");
        assertThat(provider.resolve(CredentialKey.of("empty.key"))).isEmpty();
        assertThat(provider.resolve(CredentialKey.of("unknown"))).isEmpty();
    }

    @Test
    void chainAsksProvidersInOrder() {
        CredentialProvider chain = CredentialProvider.chain(
                CredentialProvider.none(),
                StaticCredentialProvider.of(BRAVE_KEY, "first"),
                StaticCredentialProvider.of(BRAVE_KEY, "second"));

        assertThat(chain.resolve(BRAVE_KEY).get().<String>use(String::new)).isEqualTo("first");
        assertThat(chain.resolve(CredentialKey.of("none"))).isEmpty();
        assertThat(CredentialKey.of(" Brave.API_KEY ")).isEqualTo(BRAVE_KEY);
    }
}
