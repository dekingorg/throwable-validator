package org.deking.validation.validator;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.metadata.ConstraintDescriptor; 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.karlatemp.unsafeaccessor.UnsafeAccess;

public abstract class AbstractValidator<T> implements Consumer<ConstraintViolation<T>> {
	public AbstractValidator(){
		
	}
	private static final Logger LOGGER;
	static {
		LOGGER = LoggerFactory.getLogger(AbstractValidator.class);
	}

	private static class ExceptionFactory {
		@SuppressWarnings("unchecked")
		private static <A extends Annotation, E extends Throwable> Class<E> getExceptionTypeFromAnnotation(
				ConstraintDescriptor<A> constraintDescriptor) throws E {
			Annotation annotation = constraintDescriptor.getAnnotation();
			Class<A> type = (Class<A>) annotation.annotationType();
			Class<E> e = Arrays.stream(type.getDeclaredMethods()).filter(method -> {
				Type genericReturnType = method.getGenericReturnType();
				if (genericReturnType instanceof ParameterizedType parameterizedType) {
					if (parameterizedType.getRawType().equals(Class.class)) {
						Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
						if (actualTypeArgument instanceof WildcardType wildcardType) {
							var upperBounds = wildcardType.getUpperBounds();
							if (upperBounds.length > 0) {
								Class<?> clazz = (Class<?>) upperBounds[0];
								return Throwable.class.isAssignableFrom(clazz);
							}
						}
					}
				}
				return false;
			}).map(method -> {
				try {
					return (Class<E>) method.invoke(annotation);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
					LOGGER.error(e1.getMessage(), e1);
					UnsafeAccess.getInstance().getUnsafe().throwException(e1);
					return null;
				}
			}).findFirst().orElseThrow();
			return e;
		}

		public static <A extends Annotation, E extends Throwable> E create(ConstraintDescriptor<A> constraintDescriptor,
				String message) throws NoSuchElementException, E {
			Class<E> e = getExceptionTypeFromAnnotation(constraintDescriptor);
			try {
				return e.getConstructor(String.class).newInstance(message);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e1) {
				LOGGER.error(e1.getMessage(), e1);
				e1.printStackTrace();
				return null;
			}
		}
	}
 
	@Override
	public void accept(ConstraintViolation<T> constraintViolation) throws IllegalArgumentException {
		switch (constraintViolation.getPropertyPath().toString()) {
		default -> throw new IllegalArgumentException(
				constraintViolation.getPropertyPath().toString() + " " + constraintViolation.getMessage());
		}
	}

	@Inject
	private Validator validator;

	public <E extends Throwable> void validate(T obj, Class<?>... groups) throws E {
		Set<ConstraintViolation<T>> set = validator.validate(obj, groups);
		if (!set.isEmpty()) {
			ConstraintViolation<T> constraintViolation = set.iterator().next();
			String message = constraintViolation.getMessage();
			try {
				throw ExceptionFactory.create(constraintViolation.getConstraintDescriptor(), message);
			} catch (NoSuchElementException e) {  
				accept(constraintViolation);
			}
		}
	}
}