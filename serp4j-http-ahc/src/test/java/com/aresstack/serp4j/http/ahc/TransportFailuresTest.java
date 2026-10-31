package com.aresstack.serp4j.http.ahc;

import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.http.HttpTransportException.Kind;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class TransportFailuresTest {

    @Test
    void classifiesByTheMostSpecificCauseInTheChain() {
        assertThat(TransportFailures.classify(new CompletionException(new ConnectException("refused"))))
                .isEqualTo(Kind.CONNECT);
        assertThat(TransportFailures.classify(new UnknownHostException("nowhere.invalid"))).isEqualTo(Kind.CONNECT);
        assertThat(TransportFailures.classify(new ConnectTimeoutException("connect timed out")))
                .isEqualTo(Kind.TIMEOUT);
        assertThat(TransportFailures.classify(new TimeoutException("Request timeout"))).isEqualTo(Kind.TIMEOUT);
        assertThat(TransportFailures.classify(new DecoderException(new SSLHandshakeException("bad cert"))))
                .isEqualTo(Kind.TLS);
        assertThat(TransportFailures.classify(new DecoderException("bad chunk"))).isEqualTo(Kind.PROTOCOL);
        assertThat(TransportFailures.classify(new CancellationException())).isEqualTo(Kind.CANCELLED);
        assertThat(TransportFailures.classify(new IOException("reset"))).isEqualTo(Kind.IO);
        assertThat(TransportFailures.classify(new IllegalArgumentException("?"))).isEqualTo(Kind.UNKNOWN);
    }

    @Test
    void translatedExceptionCarriesRedactedUrlAndSanitizedCause() {
        HttpRequest request = HttpRequest.get("https://api.example/v1?q=x&token=abc123").build();
        IOException original = new IOException("failed for https://api.example/v1?q=x&token=abc123");

        HttpTransportException translated = TransportFailures.translate(new CompletionException(original), request);

        assertThat(translated.getKind()).isEqualTo(Kind.IO);
        assertThat(translated.getMessage()).isEqualTo("I/O failure (GET https://api.example/v1?q=x&token=****)");
        assertThat(translated.getCause()).isInstanceOf(TransportFailures.SanitizedCause.class);
        assertThat(translated.getCause().getMessage())
                .isEqualTo("java.util.concurrent.CompletionException <- java.io.IOException")
                .doesNotContain("abc123");
        assertThat(translated.getCause().getCause()).isNull();
    }

    @Test
    void ownTransportExceptionsPassThrough() {
        HttpTransportException own = new HttpTransportException(Kind.PROTOCOL, "too many redirects");
        assertThat(TransportFailures.translate(new CompletionException(own), HttpRequest.get("http://x").build()))
                .isSameAs(own);
    }
}
