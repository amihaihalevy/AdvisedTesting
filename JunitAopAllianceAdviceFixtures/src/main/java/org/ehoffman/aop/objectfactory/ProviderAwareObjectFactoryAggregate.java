package org.ehoffman.aop.objectfactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

public class ProviderAwareObjectFactoryAggregate implements ObjectFactory, ProviderAwareObjectFactory, ObjectFactoryRegistrar {

    //registrar capability, used to build the default object factory....
    LinkedHashMap<Annotation, ObjectFactory> contexts = new LinkedHashMap<>();
    
    public void register(Annotation annotation, ObjectFactory objectFactory) {
        contexts.put(annotation, objectFactory);
    }
    
    
    @Override
    public <T> T getObject(Class<T> type) {
        return contexts.entrySet().stream()
                        .map((entry) -> entry.getValue().getObject(type))
                        .filter(o -> o != null)
                        .findFirst()
                        .orElseGet(() -> null);
    }

    @Override
    public <T> T getObject(String name, Class<T> type) {
        return contexts.entrySet().stream()
                        .map((entry) -> entry.getValue().getObject(name, type))
                        .filter(o -> o != null)
                        .findFirst()
                        .orElseGet(() -> null);
    }
    
    @Override
    public <T> T getObject(Annotation annotation, Class<T> type) {
        return invokeOnFoundObjectFactory(annotation, type, o -> o.getObject(type));
    }


    @Override
    public <T> T getObject(Annotation annotation, String name, Class<T> type) {
        return invokeOnFoundObjectFactory(annotation, type, o -> o.getObject(name, type));
    }


    @Override
    public <T> Map<String, T> getAllObjects(Annotation annotation, Class<T> type) {
        return invokeOnFoundObjectFactory(annotation, type, o -> o.getAllObjects(type));
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> Map<String, T> getAllObjects(Class<T> type) {
        for (Entry<Annotation, ObjectFactory> context : contexts.entrySet()) {
            Map<String, T> matches = context.getValue().getAllObjects(type);
            if (matches != null && matches.size() > 0) {
                return matches;
            }
        }
        return Collections.unmodifiableMap(Collections.EMPTY_MAP);
    }
    
    public Object[] getArgumentsFor(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameters[i] = getArgumentFor(parameterTypes[i],  annotations[i]);
        }
        return parameters;
    }

    private Object getArgumentFor(Class<?> argumentType, Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            String requestedInstanceName = getInstanceIfPresent(annotation);
            if (requestedInstanceName != null && !"".equals(requestedInstanceName)) {
                return invokeOnFoundObjectFactory(annotation, argumentType, o -> o.getObject(requestedInstanceName, argumentType));
            }
            return invokeOnFoundObjectFactory(annotation, argumentType, o -> o.getObject(argumentType));
        }
        return this.getObject(argumentType);
    }

    private <T, X> X invokeOnFoundObjectFactory(Annotation annotation, Class<T> type, Function<ObjectFactory, X> function) {
        String desiredName = getNameIfPresent(annotation);
        for (Entry<Annotation, ObjectFactory> context : contexts.entrySet()) {
            if (context.getKey().annotationType().isAssignableFrom(annotation.annotationType()) 
                && (desiredName == null || desiredName.equals(getNameIfPresent(context.getKey())))) {
                return function.apply(context.getValue());
            }
        }
        return null;
    }

    private String getInstanceIfPresent(Annotation annotation) {
        return getValueIfPresent(annotation, "instance", String.class);        
    }
    
    private String getNameIfPresent(Annotation annotation) {
        return getValueIfPresent(annotation, "name", String.class);        
    }

    @SuppressWarnings("unchecked")
    private <T> T getValueIfPresent(Annotation annotation, String name, Class<T> output) {        
        for (Method method : annotation.annotationType().getMethods()) {
            String methodName = method.getName();
            if (name.equals(methodName) && output.isAssignableFrom(method.getReturnType())) {
                try {
                    return (T) method.invoke(annotation, (Object[])null);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    // moving right along.
                }   
            }
        }
        return null;
    }


}