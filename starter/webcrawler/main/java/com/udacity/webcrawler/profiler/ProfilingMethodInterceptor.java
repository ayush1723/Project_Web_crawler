package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Objects;
import java.lang.reflect.Proxy;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

    private final Object delegate;
    private final Clock clock;
    private final ProfilingState state;


  ProfilingMethodInterceptor(Object delegate, Clock clock, ProfilingState state) {
      this.delegate = Objects.requireNonNull(delegate);
      this.clock = Objects.requireNonNull(clock);
      this.state = Objects.requireNonNull(state);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable{
      if (method.getName().equals("equals") && method.getParameterCount() == 1) {
          Object other = args[0];
          if (other == null) {
              return false;
          }
          if (Proxy.isProxyClass(other.getClass())) {
              InvocationHandler handler = Proxy.getInvocationHandler(other);
              if (handler instanceof ProfilingMethodInterceptor) {
                  return delegate.equals(
                          ((ProfilingMethodInterceptor) handler).delegate);
              }
          }
          return delegate.equals(other);
      }

      boolean isProfiled = method.isAnnotationPresent(Profiled.class);
      java.time.Instant start = null;

      if (isProfiled) {
          start = clock.instant();
      }

      try {
          Object result = method.invoke(delegate, args);

          if (isProfiled) {
              state.record(
                      delegate.getClass(),
                      method,
                      java.time.Duration.between(start, clock.instant())
              );
          }

          return result;

      } catch (java.lang.reflect.InvocationTargetException e) {

          if (isProfiled) {
              state.record(
                      delegate.getClass(),
                      method,
                      java.time.Duration.between(start, clock.instant())
              );
          }


          throw e.getCause();
      }
  }
}
