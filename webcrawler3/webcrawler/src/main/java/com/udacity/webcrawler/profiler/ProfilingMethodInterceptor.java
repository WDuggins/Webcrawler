package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.*;
import java.util.*;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final @Inject Clock clock;
  private final ProfilingState state;
  private final Object delegate;


  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, ProfilingState state, Object delegate) {
    this.clock = Objects.requireNonNull(clock);
    this.state = state;
    this.delegate = delegate;
  }

  /* Based on Dynamic Proxy lesson, Oracle InvocationHandler documentation on invoke, and StackOverflow,
  Method interceptor should forward exceptions thrown by wrapped object,
   ZoneDateTime or Instant cannot be cast to class String, ZoneDateTime cannot be null, and start must
   be initiated for entire scope of method */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.

    Object invokedMethod;
    Instant start = null;
    if (method.getAnnotation(Profiled.class) != null) {
      start = clock.instant();
    }
    try {
      invokedMethod = method.invoke(delegate, args);
    } catch (InvocationTargetException ex) {
      throw ex.getTargetException();
    } finally {
      if (method.getAnnotation(Profiled.class) != null) {
        Duration duration = Duration.between(start, clock.instant());
        state.record(delegate.getClass(), method, duration);
      }
    }

    return invokedMethod;
  }
}
