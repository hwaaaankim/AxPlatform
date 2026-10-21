package com.axplatform;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.assertThat;
class P1CoreTest { @Test void tierIsolationCryptographicVectorsAndUntrustedInputs()throws Exception{assertThat(P1CoreAssertions.verify()).isGreaterThan(180);} }
