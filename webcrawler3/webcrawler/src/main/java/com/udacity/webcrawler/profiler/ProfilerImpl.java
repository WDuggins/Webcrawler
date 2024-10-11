package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);
  }

 /* Necessary boolean and iteration through methods for wrap method,
    Based on Reflection API lesson */
    public <T> boolean isProfiled(Class<T> klass){
      Method[] methods = klass.getDeclaredMethods();
      for (Method method : methods) {
          if (method.getAnnotation(Profiled.class) != null) {
              return true;
          }
      }return false;
  }


/* Must throw an IllegalArgumentException to prevent the following error:
    "ProfilerImplTest.delegateHasNoMethodsAnnotated:21 Profiler.wrap() should throw an IllegalArgumentException if the
    wrapped interface does not contain a @Profiled method."
  Unchecked cast: 'java.lang.Object' to 'T',
  Structure of proxy provided by Oracle Java documentation Proxy link in Performance Profiler instructions
 */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T wrap(Class<T> klass, T delegate) {
      Objects.requireNonNull(klass);

      // TODO: Use a dynamic proxy (java.lang.reflect.Proxy) to "wrap" the delegate in a
      //       ProfilingMethodInterceptor and return a dynamic proxy from this method.
      //       See https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html.

      if (!isProfiled(klass)) {
          throw new IllegalArgumentException("No profiled methods");
      }else {
          ProfilingMethodInterceptor interceptor = new ProfilingMethodInterceptor(clock, state, delegate);
          Object proxyInstance = Proxy.newProxyInstance(
                  ProfilerImpl.class.getClassLoader(),
                  new Class<?>[]{klass}, interceptor);
          return (T) proxyInstance;
      }
  }


  @Override
  public void writeData(Path path) {
    // TODO: Write the ProfilingState data to the given file path. If a file already exists at that
    //       path, the new data should be appended to the existing file.
    try(Writer writer = Files.newBufferedWriter(path)) {
      writeData(writer);
      if (Files.exists(path)){
        Files.newOutputStream(path, StandardOpenOption.APPEND);
      }
    } catch (IOException ex) {
      ex.getLocalizedMessage();
    }
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}
