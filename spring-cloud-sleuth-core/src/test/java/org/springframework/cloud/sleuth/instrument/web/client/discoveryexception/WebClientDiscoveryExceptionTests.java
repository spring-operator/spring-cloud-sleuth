/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.discoveryexception;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {
		WebClientDiscoveryExceptionTests.TestConfiguration.class }, webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = "spring.application.name=exceptionservice")
@DirtiesContext
public class WebClientDiscoveryExceptionTests {

	@Autowired TestFeignInterfaceWithException testFeignInterfaceWithException;
	@Autowired @LoadBalanced RestTemplate template;
	@Autowired Tracer tracer;
	@Rule public OutputCapture outputCapture = new OutputCapture();

	@Before
	public void open() {
		TestSpanContextHolder.removeCurrentSpan();
		ExceptionUtils.setFail(true);
	}

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	// issue #240
	private void shouldCloseSpanUponException(ResponseEntityProvider provider)
			throws IOException, InterruptedException {
		Span span = this.tracer.createSpan("new trace");

		try {
			provider.get(this);
			Assertions.fail("should throw an exception");
		}
		catch (RuntimeException e) {
		}

		assertThat(ExceptionUtils.getLastException()).isNull();

		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
		then(ExceptionUtils.getLastException()).isNull();
		// hystrix commands should finish at this point
		Thread.sleep(200);
		then(this.outputCapture.toString()).doesNotContain("Tried to detach trace span but it is not the current span");
	}

	@Test
	public void testFeignInterfaceWithException() throws Exception {
		shouldCloseSpanUponException(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException
						.shouldFailToConnect());
	}

	@Test
	public void testTemplate() throws Exception {
		shouldCloseSpanUponException((ResponseEntityProvider) (tests) -> tests.template
				.getForEntity("http://exceptionservice/", Map.class));
	}

	@FeignClient("exceptionservice")
	public interface TestFeignInterfaceWithException {
		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<String> shouldFailToConnect();
	}

	@Configuration
	@EnableAutoConfiguration(exclude = EurekaClientAutoConfiguration.class)
	@EnableDiscoveryClient
	@EnableFeignClients
	@RibbonClient("exceptionservice")
	public static class TestConfiguration {

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		Sampler alwaysSampler() {
			return new AlwaysSampler();
		}
	}

	@FunctionalInterface
	interface ResponseEntityProvider {
		ResponseEntity<?> get(WebClientDiscoveryExceptionTests webClientTests);
	}
}
