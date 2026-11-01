package com.aresstack.serp4j.core.port.credential;

import com.aresstack.serp4j.core.model.id.ProviderId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    void chainCopiesTheVarargsArrayInsteadOfAliasingIt() {
        CredentialProvider[] providers = {
                CredentialProvider.none(),
                StaticCredentialProvider.of(BRAVE_KEY, "first")
        };
        CredentialProvider chain = CredentialProvider.chain(providers);

        providers[1] = StaticCredentialProvider.of(BRAVE_KEY, "replaced");
        providers[0] = null;

        assertThat(chain.resolve(BRAVE_KEY).get().<String>use(String::new)).isEqualTo("first");
    }

    @Test
    void staticProviderKeysAreASnapshotThatSurvivesClose() {
        StaticCredentialProvider provider = StaticCredentialProvider.of(BRAVE_KEY, "abc");
        Set<CredentialKey> keys = provider.getKeys();

        provider.close();
        provider.close();

        assertThat(keys).containsExactly(BRAVE_KEY);
        assertThat(provider.getKeys()).isEmpty();
        assertThat(provider.resolve(BRAVE_KEY)).isEmpty();
        assertThat(provider.toString()).doesNotContain("abc");
    }

    @Test
    void staticProviderResolveDuringCloseNeverHandsOutHalfWipedSecrets() throws InterruptedException {
        final int threads = 4;
        final int iterations = 300;
        final StaticCredentialProvider.Builder builder = StaticCredentialProvider.builder();
        for (int i = 0; i < 64; i++) {
            builder.put(CredentialKey.of("key" + i), "value-" + i + "-" + repeat('x', 256));
        }
        final StaticCredentialProvider provider = builder.build();
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch started = new CountDownLatch(threads);
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
        List<Thread> workers = new ArrayList<Thread>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    started.countDown();
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        CredentialKey key = CredentialKey.of("key" + (i % 64));
                        provider.getKeys().size();
                        provider.toString();
                        provider.resolve(key).ifPresent(secret -> {
                            char[] chars = secret.getChars();
                            for (char c : chars) {
                                if (c == '\0') {
                                    failures.add(new AssertionError("half-wiped secret for " + key));
                                    break;
                                }
                            }
                            secret.close();
                        });
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        Thread.yield();
        provider.close();
        for (Thread worker : workers) {
            worker.join(TimeUnit.SECONDS.toMillis(10));
            assertThat(worker.isAlive()).isFalse();
        }

        assertThat(failures).isEmpty();
        assertThat(provider.resolve(CredentialKey.of("key0"))).isEmpty();
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
