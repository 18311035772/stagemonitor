package org.stagemonitor.core.instrument;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isFinal;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.Stagemonitor;
import org.stagemonitor.core.configuration.Configuration;
import org.stagemonitor.core.util.ClassUtils;

public abstract class StagemonitorByteBuddyTransformer implements AgentBuilder.Listener {

	protected final static Configuration configuration = Stagemonitor.getConfiguration();

	private static final boolean DEBUG_INSTRUMENTATION = configuration.getConfig(CorePlugin.class).isDebugInstrumentation();

	private static final Logger logger = LoggerFactory.getLogger(StagemonitorByteBuddyTransformer.class);

	public final ElementMatcher.Junction<TypeDescription> getTypeMatcher() {
		return getIncludeTypeMatcher()
				.and(noInternalJavaClasses())
				.and(not(isInterface()))
				.and(not(getExtraExcludeTypeMatcher()))
				.and(not(isSubTypeOf(StagemonitorByteBuddyTransformer.class)))
				.and(not(isSubTypeOf(StagemonitorDynamicValue.class)));
	}

	public boolean isActive() {
		return true;
	}

	protected ElementMatcher.Junction<TypeDescription> getIncludeTypeMatcher() {
		return new StagemonitorClassNameMatcher()
				.or(not(nameStartsWith("org.stagemonitor"))
						.and(noInternalJavaClasses())
						.and(getExtraIncludeTypeMatcher()));
	}

	private ElementMatcher.Junction<NamedElement> noInternalJavaClasses() {
		return not(nameStartsWith("java").or(nameStartsWith("com.sun")));
	}

	protected ElementMatcher<? super TypeDescription> getExtraIncludeTypeMatcher() {
		return none();
	}

	protected ElementMatcher<? super TypeDescription> getExtraExcludeTypeMatcher() {
		return none();
	}

	public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
		return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
			@Override
			public boolean matches(ClassLoader target) {
				return ClassUtils.canLoadClass(target, "org.stagemonitor.core.Stagemonitor");
			}
		};
	}

	public AgentBuilder.Transformer getTransformer() {
		return new AgentBuilder.Transformer() {
			@Override
			public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader) {
				List<StagemonitorDynamicValue<?>> dynamicValues = getDynamicValues();

				Advice.WithCustomMapping withCustomMapping = Advice.withCustomMapping();
				for (StagemonitorDynamicValue dynamicValue : dynamicValues) {
					withCustomMapping = withCustomMapping.bind(dynamicValue.getAnnotationClass(), dynamicValue);
				}

				return builder
						.visit(withCustomMapping
								.to(getAdviceClass())
								.on(getMethodElementMatcher()));
			}

		};
	}

	protected List<StagemonitorDynamicValue<?>> getDynamicValues() {
		return Collections.emptyList();
	}


	protected ElementMatcher.Junction<? super MethodDescription.InDefinedShape> getMethodElementMatcher() {
		return not(isConstructor())
				.and(not(isAbstract()))
				.and(not(isNative()))
				.and(not(isFinal()))
				.and(not(isSynthetic()))
				.and(not(isTypeInitializer()))
				.and(getExtraMethodElementMatcher());
	}

	protected ElementMatcher.Junction<? super MethodDescription.InDefinedShape> getExtraMethodElementMatcher() {
		return any();
	}

	protected Class<? extends StagemonitorByteBuddyTransformer> getAdviceClass() {
		return getClass();
	}

	public abstract static class StagemonitorDynamicValue<T extends Annotation> implements Advice.DynamicValue<T> {
		public abstract Class<T> getAnnotationClass();
	}

	@Override
	public void onTransformation(TypeDescription typeDescription, DynamicType dynamicType) {
		if (DEBUG_INSTRUMENTATION) {
			logger.info("Transformed {} with {}", typeDescription.getName(), getClass().getSimpleName());
		}
	}

	@Override
	public void onIgnored(TypeDescription typeDescription) {
	}

	@Override
	public void onError(String typeName, Throwable throwable) {
		if (DEBUG_INSTRUMENTATION) {
			logger.warn(typeName, throwable);
		}
	}

	@Override
	public void onComplete(String typeName) {
	}
}
