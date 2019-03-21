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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.httpasyncclient.TracingHttpAsyncClientBuilder;
import brave.httpclient.TracingHttpClientBuilder;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.cloud.commons.httpclient.HttpClientConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using
 * {@link RestTemplate}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@SleuthWebClientEnabled
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureAfter(TraceWebServletAutoConfiguration.class)
@AutoConfigureBefore(HttpClientConfiguration.class)
public class TraceWebClientAutoConfiguration {

	@Configuration
	@ConditionalOnClass(RestTemplate.class)
	static class RestTemplateConfig {

		@Bean
		public TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor(
				HttpTracing httpTracing) {
			return (TracingClientHttpRequestInterceptor) TracingClientHttpRequestInterceptor
					.create(httpTracing);
		}

		@Configuration
		protected static class TraceInterceptorConfiguration {

			@Autowired
			private TracingClientHttpRequestInterceptor clientInterceptor;

			@Bean
			static TraceRestTemplateBeanPostProcessor traceRestTemplateBPP(
					ListableBeanFactory beanFactory) {
				return new TraceRestTemplateBeanPostProcessor(beanFactory);
			}

			@Bean
			@Order
			RestTemplateCustomizer traceRestTemplateCustomizer() {
				return new TraceRestTemplateCustomizer(this.clientInterceptor);
			}

		}

	}

	@Configuration
	@ConditionalOnClass(HttpClientBuilder.class)
	static class HttpClientBuilderConfig {

		@Bean
		@ConditionalOnMissingBean
		HttpClientBuilder traceHttpClientBuilder(HttpTracing httpTracing) {
			return TracingHttpClientBuilder.create(httpTracing);
		}

	}

	@Configuration
	@ConditionalOnClass(HttpAsyncClientBuilder.class)
	static class HttpAsyncClientBuilderConfig {

		@Bean
		@ConditionalOnMissingBean
		HttpAsyncClientBuilder traceHttpAsyncClientBuilder(HttpTracing httpTracing) {
			return TracingHttpAsyncClientBuilder.create(httpTracing);
		}

	}

	@Configuration
	@ConditionalOnClass(WebClient.class)
	static class WebClientConfig {

		@Bean
		static TraceWebClientBeanPostProcessor traceWebClientBeanPostProcessor(
				BeanFactory beanFactory) {
			return new TraceWebClientBeanPostProcessor(beanFactory);
		}

	}

	@Configuration
	@ConditionalOnClass(HttpClient.class)
	static class NettyConfiguration {

		@Bean
		public HttpClientBeanPostProcessor httpClientBeanPostProcessor(BeanFactory beanFactory) {
			return new HttpClientBeanPostProcessor(beanFactory);
		}

	}

	@Configuration
	@ConditionalOnClass({ UserInfoRestTemplateCustomizer.class,
			OAuth2RestTemplate.class })
	protected static class TraceOAuthConfiguration {

		@Bean
		UserInfoRestTemplateCustomizerBPP userInfoRestTemplateCustomizerBeanPostProcessor(
				BeanFactory beanFactory) {
			return new UserInfoRestTemplateCustomizerBPP(beanFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		UserInfoRestTemplateCustomizer traceUserInfoRestTemplateCustomizer(
				BeanFactory beanFactory) {
			return new TraceUserInfoRestTemplateCustomizer(beanFactory);
		}

		private static class UserInfoRestTemplateCustomizerBPP
				implements BeanPostProcessor {

			private final BeanFactory beanFactory;

			UserInfoRestTemplateCustomizerBPP(BeanFactory beanFactory) {
				this.beanFactory = beanFactory;
			}

			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName)
					throws BeansException {
				return bean;
			}

			@Override
			public Object postProcessAfterInitialization(final Object bean,
					String beanName) throws BeansException {
				final BeanFactory beanFactory = this.beanFactory;
				if (bean instanceof UserInfoRestTemplateCustomizer
						&& !(bean instanceof TraceUserInfoRestTemplateCustomizer)) {
					return new TraceUserInfoRestTemplateCustomizer(beanFactory, bean);
				}
				return bean;
			}

		}

	}

}

class RestTemplateInterceptorInjector {

	private final ClientHttpRequestInterceptor interceptor;

	RestTemplateInterceptorInjector(ClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	void inject(RestTemplate restTemplate) {
		if (hasTraceInterceptor(restTemplate)) {
			return;
		}
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(
				restTemplate.getInterceptors());
		interceptors.add(0, this.interceptor);
		restTemplate.setInterceptors(interceptors);
	}

	private boolean hasTraceInterceptor(RestTemplate restTemplate) {
		for (ClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors()) {
			if (interceptor instanceof TracingClientHttpRequestInterceptor) {
				return true;
			}
		}
		return false;
	}

}

class TraceRestTemplateCustomizer implements RestTemplateCustomizer {

	private final TracingClientHttpRequestInterceptor interceptor;

	TraceRestTemplateCustomizer(TracingClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override
	public void customize(RestTemplate restTemplate) {
		new RestTemplateInterceptorInjector(this.interceptor).inject(restTemplate);
	}

}

class TraceRestTemplateBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	TraceRestTemplateBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RestTemplate) {
			RestTemplate rt = (RestTemplate) bean;
			new RestTemplateInterceptorInjector(interceptor()).inject(rt);
		}
		return bean;
	}

	private LazyTracingClientHttpRequestInterceptor interceptor() {
		return new LazyTracingClientHttpRequestInterceptor(this.beanFactory);
	}

}

class LazyTracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	private final BeanFactory beanFactory;

	private TracingClientHttpRequestInterceptor interceptor;

	public LazyTracingClientHttpRequestInterceptor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		return interceptor().intercept(request, body, execution);
	}

	private TracingClientHttpRequestInterceptor interceptor() {
		if (this.interceptor == null) {
			this.interceptor = this.beanFactory
					.getBean(TracingClientHttpRequestInterceptor.class);
		}
		return this.interceptor;
	}
}


class HttpClientBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	HttpClientBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof HttpClient) {
			return ((HttpClient) bean)
					.doOnRequest(TracingDoOnRequest.create(this.beanFactory))
					.doOnResponse(TracingDoOnResponse.create(this.beanFactory));
		}
		return bean;
	}
}

class TracingDoOnRequest implements BiConsumer<HttpClientRequest, Connection> {

	private static final Logger log = LoggerFactory.getLogger(TracingDoOnRequest.class);

	static final Propagation.Setter<HttpHeaders, String> SETTER = new Propagation.Setter<HttpHeaders, String>() {
		@Override
		public void put(HttpHeaders carrier, String key, String value) {
			if (!carrier.contains(key)) {
				carrier.add(key, value);
			}
		}

		@Override
		public String toString() {
			return "HttpHeaders::add";
		}
	};
	static final Propagation.Getter<HttpHeaders, String> GETTER = new Propagation.Getter<HttpHeaders, String>() {
		@Override
		public String get(HttpHeaders carrier, String key) {
			return carrier.get(key);
		}

		@Override
		public String toString() {
			return "HttpHeaders::get";
		}
	};

	final Tracer tracer;
	final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
	final TraceContext.Injector<HttpHeaders> injector;
	final HttpTracing httpTracing;

	TracingDoOnRequest(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
		this.injector = httpTracing.tracing().propagation().injector(SETTER);
		this.httpTracing = httpTracing;
	}

	static TracingDoOnRequest create(BeanFactory beanFactory) {
		return new TracingDoOnRequest(beanFactory.getBean(HttpTracing.class));
	}

	@Override
	public void accept(HttpClientRequest req, Connection connection) {
		final Span currentSpan = this.tracer.currentSpan();
		try (Tracer.SpanInScope spanInScope = this.tracer
				.withSpanInScope(currentSpan)) {
			if (log.isDebugEnabled()) {
				log.debug("Wrapping do on request");
			}
			Span span = this.handler.handleSend(this.injector, req
					.requestHeaders(), req);
			Attribute<Object> attribute = connection.channel()
					.attr(AttributeKey.valueOf("span"));
			attribute.set(span);
		}
	}
}

class TracingDoOnResponse implements BiConsumer<HttpClientResponse, Connection> {

	private static final Logger log = LoggerFactory.getLogger(TracingDoOnResponse.class);

	final Tracer tracer;
	final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

	TracingDoOnResponse(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
	}

	static TracingDoOnResponse create(BeanFactory beanFactory) {
		return new TracingDoOnResponse(beanFactory.getBean(HttpTracing.class));
	}

	@Override
	public void accept(HttpClientResponse httpClientResponse, Connection connection) {
		Attribute<Object> spanAttr = connection.channel().attr(AttributeKey.valueOf("span"));
		Span span = (Span) spanAttr.get();
		if (span == null) {
			return;
		}
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			if (log.isDebugEnabled()) {
				log.debug("Setting client sent spans");
			}
			// status codes and CR
			// TODO: Add throwable
			this.handler.handleReceive(httpClientResponse, null, span);
		}
	}
}

class HttpAdapter
		extends brave.http.HttpClientAdapter<HttpClientRequest, HttpClientResponse> {

	@Override
	public String method(HttpClientRequest request) {
		return request.method().name();
	}

	@Override
	public String url(HttpClientRequest request) {
		return request.uri();
	}

	@Override
	public String requestHeader(HttpClientRequest request, String name) {
		Object result = request.requestHeaders().get(name);
		return result != null ? result.toString() : "";
	}

	@Override
	public Integer statusCode(HttpClientResponse response) {
		return response.status().code();
	}

}

class TraceUserInfoRestTemplateCustomizer implements UserInfoRestTemplateCustomizer {

	private final BeanFactory beanFactory;

	private final Object delegate;

	TraceUserInfoRestTemplateCustomizer(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.delegate = null;
	}

	TraceUserInfoRestTemplateCustomizer(BeanFactory beanFactory, Object bean) {
		this.beanFactory = beanFactory;
		this.delegate = bean;
	}

	@Override
	public void customize(OAuth2RestTemplate template) {
		final TracingClientHttpRequestInterceptor interceptor = this.beanFactory
				.getBean(TracingClientHttpRequestInterceptor.class);
		new RestTemplateInterceptorInjector(interceptor).inject(template);
		if (this.delegate != null) {
			((UserInfoRestTemplateCustomizer) this.delegate).customize(template);
		}
	}

}
