/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.security.authorization.method;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

import reactor.util.annotation.NonNull;

import org.springframework.context.ApplicationContext;
import org.springframework.expression.Expression;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.core.annotation.AnnotationSynthesizer;
import org.springframework.security.core.annotation.AnnotationSynthesizers;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 * @author DingHao
 * @since 5.8
 */
final class PostAuthorizeExpressionAttributeRegistry extends AbstractExpressionAttributeRegistry<ExpressionAttribute> {

	private final MethodAuthorizationDeniedHandler defaultHandler = new ThrowingMethodAuthorizationDeniedHandler();

	private final AnnotationSynthesizer<HandleAuthorizationDenied> handleAuthorizationDeniedSynthesizer = AnnotationSynthesizers
		.requireUnique(HandleAuthorizationDenied.class);

	private Function<Class<? extends MethodAuthorizationDeniedHandler>, MethodAuthorizationDeniedHandler> handlerResolver;

	private AnnotationSynthesizer<PostAuthorize> postAuthorizeSynthesizer = AnnotationSynthesizers
		.requireUnique(PostAuthorize.class);

	PostAuthorizeExpressionAttributeRegistry() {
		this.handlerResolver = (clazz) -> new ReflectiveMethodAuthorizationDeniedHandler(clazz,
				PostAuthorizeAuthorizationManager.class);
	}

	@NonNull
	@Override
	ExpressionAttribute resolveAttribute(Method method, Class<?> targetClass) {
		PostAuthorize postAuthorize = findPostAuthorizeAnnotation(method, targetClass);
		if (postAuthorize == null) {
			return ExpressionAttribute.NULL_ATTRIBUTE;
		}
		Expression expression = getExpressionHandler().getExpressionParser().parseExpression(postAuthorize.value());
		MethodAuthorizationDeniedHandler deniedHandler = resolveHandler(method, targetClass);
		return new PostAuthorizeExpressionAttribute(expression, deniedHandler);
	}

	private MethodAuthorizationDeniedHandler resolveHandler(Method method, Class<?> targetClass) {
		Class<?> targetClassToUse = targetClass(method, targetClass);
		HandleAuthorizationDenied deniedHandler = this.handleAuthorizationDeniedSynthesizer.synthesize(method,
				targetClassToUse);
		if (deniedHandler != null) {
			return this.handlerResolver.apply(deniedHandler.handlerClass());
		}
		return this.defaultHandler;
	}

	private PostAuthorize findPostAuthorizeAnnotation(Method method, Class<?> targetClass) {
		Class<?> targetClassToUse = targetClass(method, targetClass);
		return this.postAuthorizeSynthesizer.synthesize(method, targetClass);
	}

	/**
	 * Uses the provided {@link ApplicationContext} to resolve the
	 * {@link MethodAuthorizationDeniedHandler} from {@link PostAuthorize}
	 * @param context the {@link ApplicationContext} to use
	 */
	void setApplicationContext(ApplicationContext context) {
		Assert.notNull(context, "context cannot be null");
		this.handlerResolver = (clazz) -> resolveHandler(context, clazz);
	}

	void setTemplateDefaults(AnnotationTemplateExpressionDefaults templateDefaults) {
		this.postAuthorizeSynthesizer = AnnotationSynthesizers.requireUnique(PostAuthorize.class, templateDefaults);
	}

	private MethodAuthorizationDeniedHandler resolveHandler(ApplicationContext context,
			Class<? extends MethodAuthorizationDeniedHandler> handlerClass) {
		if (handlerClass == this.defaultHandler.getClass()) {
			return this.defaultHandler;
		}
		String[] beanNames = context.getBeanNamesForType(handlerClass);
		if (beanNames.length == 0) {
			throw new IllegalStateException("Could not find a bean of type " + handlerClass.getName());
		}
		if (beanNames.length > 1) {
			throw new IllegalStateException("Expected to find a single bean of type " + handlerClass.getName()
					+ " but found " + Arrays.toString(beanNames));
		}
		return context.getBean(beanNames[0], handlerClass);
	}

}
