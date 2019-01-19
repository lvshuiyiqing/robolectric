package org.robolectric.plugins;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import javax.annotation.Nonnull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;
import org.robolectric.SingleSdkRobolectricTestRunner;
import org.robolectric.android.FailureListener;
import org.robolectric.config.ConfigRegistry;
import org.robolectric.pluginapi.ConfigStrategy;
import org.robolectric.pluginapi.Configurer;

@RunWith(JUnit4.class)
public class CustomConfigurerTest {

  @Test
  public void customConfigCanBeAccessedFromWithinSandbox() throws Exception {
    List<String> failures = runAndGetFailures(TestWithConfig.class);
    assertThat(failures).containsExactly("someConfig value is the value");
  }

  /////////////////////

  @Retention(RetentionPolicy.RUNTIME)
  @Target(value = {ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD})
  public @interface SomeConfig {
    String value();
  }

  @Ignore
  public static class TestWithConfig {

    @Test
    @SomeConfig(value = "the value")
    public void shouldHaveValue() throws Exception {
      SomeConfig someConfig = ConfigRegistry.get(SomeConfig.class);
      fail("someConfig value is " + someConfig.value());
    }
  }


  static class SomeConfigConfigurer implements Configurer<SomeConfig> {

    @Override
    public Class<SomeConfig> getConfigClass() {
      return SomeConfig.class;
    }

    @Nonnull
    @Override
    public SomeConfig defaultConfig() {
      return new SomeConfig() {

        @Override
        public Class<? extends Annotation> annotationType() {
          return Annotation.class;
        }

        @Override
        public String value() {
          return "default value";
        }
      };
    }

    @Override
    public SomeConfig getConfigFor(@Nonnull String packageName) {
      return null;
    }

    @Override
    public SomeConfig getConfigFor(@Nonnull Class<?> testClass) {
      return testClass.getAnnotation(SomeConfig.class);
    }

    @Override
    public SomeConfig getConfigFor(@Nonnull Method method) {
      return method.getAnnotation(SomeConfig.class);
    }

    @Nonnull
    @Override
    public SomeConfig merge(@Nonnull SomeConfig parentConfig, @Nonnull SomeConfig childConfig) {
      return childConfig;
    }
  }

  private List<String> runAndGetFailures(Class<TestWithConfig> testClass)
      throws InitializationError {
    RunNotifier notifier = new RunNotifier();
    FailureListener failureListener = new FailureListener();
    notifier.addListener(failureListener);

    HierarchicalConfigStrategy configStrategy =
        new HierarchicalConfigStrategy(
            new ConfigConfigurer(new PackagePropertiesLoader()),
            new SomeConfigConfigurer());

    SingleSdkRobolectricTestRunner testRunner = new SingleSdkRobolectricTestRunner(
        testClass,
        SingleSdkRobolectricTestRunner.defaultInjector()
            .register(ConfigStrategy.class, configStrategy));

    testRunner.run(notifier);
    return failureListener.failures.stream().map(Failure::getMessage).collect(toList());
  }

}
