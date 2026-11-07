package com.your.summoner.processor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ApplicationSmokeTest {
  @LocalServerPort private int port;
  @Autowired private TestRestTemplate restTemplate;
  @Test void healthEndpointIsAvailable() {
    ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/processor/actuator/health", String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }
}
