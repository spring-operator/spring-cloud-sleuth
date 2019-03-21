/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client.feign.servererrors;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import feign.Logger;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * Related to https://github.com/spring-cloud/spring-cloud-sleuth/issues/257
 *
 * @author ryarabori
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FeignClientServerErrorTests.TestConfiguration.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"spring.application.name=fooservice",
		"spring.sleuth.sampler.probability=1.0",
		"feign.hystrix.enabled=true",
		"spring.sleuth.http.legacy.enabled=true",
		"hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds=60000",
		"hystrix.command.default.execution.isolation.strategy=SEMAPHORE"})
@DirtiesContext
public class FeignClientServerErrorTests {

	private static final Log log = LogFactory.getLog(FeignClientServerErrorTests.class);

	@Autowired TestFeignInterface feignInterface;
	@Autowired TestFeignWithCustomConfInterface customConfFeignInterface;
	@Autowired ArrayListSpanReporter reporter;
	@Autowired Tracer tracer;

	@Before
	public void setup() {
		this.reporter.clear();
	}

	@Test
	public void shouldCloseSpanOnInternalServerError(){
		try(Tracer.SpanInScope ws = tracer.withSpanInScope(tracer.nextSpan().name("foo").start())) {
			log.info("sending a request");
			this.feignInterface.internalError();
			fail("Must throw an exception");
		} catch (HystrixRuntimeException e) {
			log.info("Expected exception thrown", e);
		}

		Awaitility.await().untilAsserted(() -> {
			List<Span> spans = this.reporter.getSpans();
			log.info("Spans " + spans);
			Optional<Span> spanWithError = spans.stream()
					.filter(span -> span.tags().containsKey("error")).findFirst();
			then(spanWithError.isPresent()).isTrue();
			then(spanWithError.get().tags())
					.containsEntry("error", "500")
					.containsEntry("http.status_code", "500");
		});
	}

	@Test
	public void shouldCloseSpanOnNotFound() {
		try(Tracer.SpanInScope ws = tracer.withSpanInScope(tracer.nextSpan().name("foo").start())) {
			log.info("sending a request");
			this.feignInterface.notFound();
			fail("Must throw an exception");
		} catch (HystrixRuntimeException e) {
			log.info("Expected exception thrown", e);
		}

		Awaitility.await().untilAsserted(() -> {
			List<Span> spans = this.reporter.getSpans();
			log.info("Spans " + spans);
			Optional<Span> spanWithError = spans.stream()
					.filter(span -> span.tags().containsKey("http.status_code")).findFirst();
			then(spanWithError.isPresent()).isTrue();
			then(spanWithError.get().tags())
					.containsEntry("http.status_code", "404");
		});
	}

	@Test
	public void shouldCloseSpanOnOk() {
		try(Tracer.SpanInScope ws = tracer.withSpanInScope(tracer.nextSpan().name("foo").start())) {
			log.info("sending a request");
			this.feignInterface.ok();
		} catch (HystrixRuntimeException e) {
			log.info("Expected exception thrown", e);
		}

		Awaitility.await().untilAsserted(() -> {
			List<Span> spans = this.reporter.getSpans();
			log.info("Spans " + spans);
			then(spans.size()).isEqualTo(1);
			Optional<Span> httpSpan = spans.stream()
					.filter(span -> span.tags().containsKey("http.method")).findFirst();
			then(httpSpan.isPresent()).isTrue();
			then(httpSpan.get().tags())
					.containsEntry("http.method", "GET")
					.doesNotContainEntry("http.url", "http://fooservice/ok");
		});
	}

	@Test
	public void shouldCloseSpanOnOkWithCustomFeignConfiguration(){
		try(Tracer.SpanInScope ws = tracer.withSpanInScope(tracer.nextSpan().name("foo").start())) {
			log.info("sending a request");
			this.customConfFeignInterface.ok();
			fail("Must throw an exception");
		} catch (HystrixRuntimeException e) {
			log.info("Expected exception thrown", e);
		}

		Awaitility.await().untilAsserted(() -> {
			List<Span> spans = this.reporter.getSpans();
			log.info("Spans " + spans);
			then(spans.size()).isGreaterThanOrEqualTo(1);
			Optional<Span> httpSpan = spans.stream()
					.filter(span -> span.tags().containsKey("http.method")).findFirst();
			then(httpSpan.isPresent()).isTrue();
			then(httpSpan.get().tags())
					.containsEntry("http.method", "GET");
		});
	}

	@Test
	public void shouldCloseSpanOnNotFoundWithCustomFeignConfiguration(){
		try(Tracer.SpanInScope ws = tracer.withSpanInScope(tracer.nextSpan().name("foo").start())) {
			log.info("sending a request");
			this.customConfFeignInterface.notFound();
			fail("Must throw an exception");
		} catch (HystrixRuntimeException e) {
			log.info("Expected exception thrown", e);
		}

		Awaitility.await().untilAsserted(() -> {
			List<Span> spans = this.reporter.getSpans();
			log.info("Spans " + spans);
			Optional<Span> spanWithError = spans.stream()
					.filter(span -> span.tags().containsKey("error")).findFirst();
			then(spanWithError.isPresent()).isTrue();
			then(spanWithError.get().tags())
					.containsEntry("error", "404")
					.containsEntry("http.status_code", "404");
		});
	}

	@Configuration
	@EnableAutoConfiguration(exclude = TraceWebServletAutoConfiguration.class)
	@EnableFeignClients
	@RibbonClients({@RibbonClient(value = "fooservice",
			configuration = SimpleRibbonClientConfiguration.class),
			@RibbonClient(value = "customConfFooService",
			configuration = SimpleRibbonClientConfiguration.class)})
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}

		@Bean
		ArrayListSpanReporter listener() {
			return new ArrayListSpanReporter();
		}

		@LoadBalanced
		@Bean
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		Logger.Level feignLoggerLevel() {
			return Logger.Level.FULL;
		}
	}

	@FeignClient(value = "fooservice")
	public interface TestFeignInterface {

		@RequestMapping(method = RequestMethod.GET, value = "/internalerror")
		ResponseEntity<String> internalError();

		@RequestMapping(method = RequestMethod.GET, value = "/notfound")
		ResponseEntity<String> notFound();

		@RequestMapping(method = RequestMethod.GET, value = "/ok")
		ResponseEntity<String> ok();
	}

	@FeignClient(value = "customConfFooService", configuration = CustomFeignClientConfiguration.class)
	public interface TestFeignWithCustomConfInterface {

		@RequestMapping(method = RequestMethod.GET, value = "/notfound")
		ResponseEntity<String> notFound();

		@RequestMapping(method = RequestMethod.GET, value = "/ok")
		ResponseEntity<String> ok();
	}

	@Configuration
	public static class CustomFeignClientConfiguration {
		@Bean
		Decoder decoder() {
			return new Decoder.Default();
		}

		@Bean
		ErrorDecoder errorDecoder() {
			return new ErrorDecoder.Default();
		}
	}

	@RestController
	public static class FooController {

		@Autowired Tracing tracer;

		@RequestMapping("/internalerror")
		public ResponseEntity<String> internalError(
				@RequestHeader("X-B3-TraceId") String traceId,
				@RequestHeader("X-B3-SpanId") String spanId,
				@RequestHeader("X-B3-ParentSpanId") String parentId) {
			log.info("Will respond with internal error");
			logHeaders(traceId, spanId, parentId);
			throw new RuntimeException("Internal Error");
		}

		@RequestMapping("/notfound")
		public ResponseEntity<String> notFound(
				@RequestHeader("X-B3-TraceId") String traceId,
				@RequestHeader("X-B3-SpanId") String spanId,
				@RequestHeader("X-B3-ParentSpanId") String parentId) {
			log.info("Will respond with not found");
			logHeaders(traceId, spanId, parentId);
			return new ResponseEntity<>("not found", HttpStatus.NOT_FOUND);
		}

		@RequestMapping("/ok")
		public ResponseEntity<String> ok(
				@RequestHeader("X-B3-TraceId") String traceId,
				@RequestHeader("X-B3-SpanId") String spanId,
				@RequestHeader("X-B3-ParentSpanId") String parentId) {
			log.info("Will respond with OK");
			logHeaders(traceId, spanId, parentId);
			return new ResponseEntity<>("ok", HttpStatus.OK);
		}

		private void logHeaders(String traceId, String spanId, String parentId) {
			log.info("Trace [" + traceId + "], span [" + spanId + "], parent [" + parentId + "]");
		}
	}

	@Configuration
	public static class SimpleRibbonClientConfiguration {

		@Value("${local.server.port}")
		private int port = 0;

		@Bean
		public ILoadBalancer ribbonLoadBalancer() {
			BaseLoadBalancer balancer = new BaseLoadBalancer();
			balancer.setServersList(
					Collections.singletonList(new Server("localhost", this.port)));
			return balancer;
		}
	}

}
