/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enabling async related processing.
 *
 * @author Dave Syer
 * @author Marcin Grzejszczak
 * @since 1.0.0
 *
 * @see LazyTraceExecutor
 * @see TraceAsyncAspect
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.async.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(AsyncCustomAutoConfiguration.class)
public class AsyncDefaultAutoConfiguration {

	@Configuration
	@ConditionalOnMissingBean(AsyncConfigurer.class)
	@ConditionalOnProperty(value = "spring.sleuth.async.configurer.enabled", matchIfMissing = true)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static class DefaultAsyncConfigurerSupport extends AsyncConfigurerSupport {

		@Autowired private BeanFactory beanFactory;

		@Override
		public Executor getAsyncExecutor() {
			return new LazyTraceExecutor(this.beanFactory, new SimpleAsyncTaskExecutor());
		}
	}

	@Autowired private BeanFactory beanFactory;

	@Bean
	public TraceAsyncAspect traceAsyncAspect(Tracer tracer, TraceKeys traceKeys) {
		return new TraceAsyncAspect(tracer, traceKeys, this.beanFactory);
	}

	@Bean
	public static ExecutorBeanPostProcessor executorBeanPostProcessor(BeanFactory beanFactory) {
		return new ExecutorBeanPostProcessor(beanFactory);
	}

}