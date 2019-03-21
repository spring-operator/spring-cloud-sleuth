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

package org.springframework.cloud.sleuth.instrument.async;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.Executor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ReflectionUtils;

/**
 * Bean post processor that wraps a call to an {@link Executor} either in a JDK or CGLIB
 * proxy. Depending on whether the implementation has a final method or is final.
 *
 * @author Marcin Grzejszczak
 * @author Jesus Alonso
 * @author Denys Ivano
 * @since 1.1.4
 */
class ExecutorBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(ExecutorBeanPostProcessor.class);

	private final BeanFactory beanFactory;

	private SleuthAsyncProperties sleuthAsyncProperties;

	ExecutorBeanPostProcessor(BeanFactory beanFactory) {
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
		if (bean instanceof Executor && !(bean instanceof ThreadPoolTaskExecutor)) {
			Method execute = ReflectionUtils.findMethod(bean.getClass(), "execute",
					Runnable.class);
			boolean methodFinal = Modifier.isFinal(execute.getModifiers());
			boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
			boolean cglibProxy = !methodFinal && !classFinal;
			Executor executor = (Executor) bean;
			try {
				return createProxy(bean, cglibProxy, executor);
			}
			catch (AopConfigException ex) {
				if (cglibProxy) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Exception occurred while trying to create a proxy, falling back to JDK proxy",
								ex);
					}
					return createProxy(bean, false, executor);
				}
				throw ex;
			}
		}
		else if (bean instanceof ThreadPoolTaskExecutor) {
			if (isProxyNeeded(beanName)) {
				boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
				boolean cglibProxy = !classFinal;
				ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
				return createThreadPoolTaskExecutorProxy(bean, cglibProxy, executor);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		return bean;
	}

	boolean isProxyNeeded(String beanName) {
		SleuthAsyncProperties sleuthAsyncProperties = asyncConfigurationProperties();
		return !sleuthAsyncProperties.getIgnoredBeans().contains(beanName);
	}

	Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy,
			ThreadPoolTaskExecutor executor) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(new ExecutorMethodInterceptor<ThreadPoolTaskExecutor>(executor,
				this.beanFactory) {
			@Override
			Executor executor(BeanFactory beanFactory, ThreadPoolTaskExecutor executor) {
				return new LazyTraceThreadPoolTaskExecutor(beanFactory, executor);
			}
		});
		factory.setTarget(bean);
		return factory.getObject();
	}

	@SuppressWarnings("unchecked")
	Object createProxy(Object bean, boolean cglibProxy, Executor executor) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(new ExecutorMethodInterceptor(executor, this.beanFactory));
		factory.setTarget(bean);
		return factory.getObject();
	}

	private SleuthAsyncProperties asyncConfigurationProperties() {
		if (this.sleuthAsyncProperties == null) {
			this.sleuthAsyncProperties = this.beanFactory
					.getBean(SleuthAsyncProperties.class);
		}
		return this.sleuthAsyncProperties;
	}

}

/**
 * Interceptor for executor methods.
 *
 * @param <T> - executor type
 * @author Marcin Grzejszczak
 */
class ExecutorMethodInterceptor<T extends Executor> implements MethodInterceptor {

	private final T delegate;

	private final BeanFactory beanFactory;

	ExecutorMethodInterceptor(T delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Executor executor = executor(this.beanFactory, this.delegate);
		Method methodOnTracedBean = getMethod(invocation, executor);
		if (methodOnTracedBean != null) {
			try {
				return methodOnTracedBean.invoke(executor, invocation.getArguments());
			}
			catch (InvocationTargetException ex) {
				// gh-1092: throw the target exception (if present)
				Throwable cause = ex.getCause();
				throw (cause != null) ? cause : ex;
			}
		}
		return invocation.proceed();
	}

	private Method getMethod(MethodInvocation invocation, Object object) {
		Method method = invocation.getMethod();
		return ReflectionUtils.findMethod(object.getClass(), method.getName(),
				method.getParameterTypes());
	}

	Executor executor(BeanFactory beanFactory, T executor) {
		return new LazyTraceExecutor(beanFactory, executor);
	}

}
