/*
 * The MIT License
 * Copyright © 2016 Rex Hoffman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.ehoffman.testclassloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;

import org.ehoffman.aop.context.IoCContext;
import org.ehoffman.classloader.ClassContainsStaticInitialization;
import org.ehoffman.classloader.EvictingClassLoader;
import org.ehoffman.classloader.EvictingStaticTransformer;
import org.ehoffman.classloader.RestrictiveClassloader;
import org.ehoffman.junit.aop.Junit4AopClassRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;

import test.classloader.data.BadAppConfig;
import test.classloader.data.ContainsAssertion;
import test.classloader.data.ContainsStaticFinalLiteral;
import test.classloader.data.ContainsStaticFinalNonLiteral;
import test.classloader.data.ContainsStaticLiteralNonFinal;
import test.classloader.data.ContainsStaticUnsetVar;
import test.classloader.data.NestedContainsStaticNonFinalOrNonLiteral;
import test.classloader.data.StaticInitBlockClass;

@RunWith(Junit4AopClassRunner.class)
public class TestStaticInitializationEvictionJunit4 {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();
  
  @Test
  public void testClassContainsStaticInitializationPredicate() throws IOException {
    ClassContainsStaticInitialization asmScanner = new ClassContainsStaticInitialization();
    assertThat(asmScanner.test(ContainsStaticUnsetVar.class.getName()))
        .describedAs("Classes that contains statics should be evicted").isTrue(); 
    assertThat(asmScanner.test(ContainsStaticLiteralNonFinal.class.getName()))
        .describedAs("Static literal non final fields should cause classes should be evicted").isTrue();
    assertThat(asmScanner.test(ContainsStaticFinalNonLiteral.class.getName()))
        .describedAs("Static final non literal fields should cause class to be evicted").isTrue();
    assertThat(asmScanner.test(StaticInitBlockClass.class.getName()))
        .describedAs("Static init block should cause class to be evicted").isTrue();
    assertThat(asmScanner.test(NestedContainsStaticNonFinalOrNonLiteral.Nested.class.getName()))
        .describedAs("Nested classes are evicted as well").isTrue();
    assertThat(asmScanner.test(ContainsStaticFinalLiteral.class.getName()))
        .describedAs("Static final literal containing classes are not evicted").isFalse();
    assertThat(asmScanner.test(NestedContainsStaticNonFinalOrNonLiteral.class.getName()))
        .describedAs("Classes that contain bad nested classes are not prevented").isFalse();
    assertThat(asmScanner.test(ContainsAssertion.class.getName()))
        .describedAs("Classes with assertions are permitted").isFalse();
  }

  @Test
  public void testSimpleClassloaderChecks() throws ClassNotFoundException {
    EvictingClassLoader loader = new EvictingClassLoader(new ArrayList<>(), new EvictingStaticTransformer(),
            this.getClass().getClassLoader());
    loader.loadClass(TestStaticInitializationEvictionJunit4.class.getName());
    assertThatThrownBy(() -> loader.loadClass(ContainsStaticLiteralNonFinal.class.getName())).isInstanceOf(ClassFormatError.class);
  }

  @Test
  @RestrictiveClassloader
  @IoCContext(name = "bob", classes = { test.classloader.data.AppConfiguration.class })
  @IoCContext(name = "ted", classes = { test.classloader.data.AppConfiguration.class })
  public void testGoodContext(ApplicationContext context, test.classloader.data.AppConfiguration.TestBean bean) {
    assertThat(bean.getClass().getClassLoader().getClass().getName()).isEqualTo(EvictingClassLoader.class.getName());
    assertThat(context).isNotNull();
    assertThat(bean).isNotNull();
  }
  
  /**
   * <p>
   * Even a parameter like: @IoCContext(instance = "badApple") Object bean would trip up the classloader, just
   * earlier in the test execution than the expected exception handling could deal with.
   * </p>
   * <p> 
   * Usually devs would list "badApple" by type which would cause the test class to fail to load (for all tests
   * in the restrictive class loader), but it is possible that an interface could be used to get a bean, and that
   * the implementation would trip up the rules.
   * </p>
   */
  @Test(expected = BeanCreationException.class)
  @RestrictiveClassloader
  @IoCContext(name = "bob", classes = { BadAppConfig.class })
  public void testBadContext(ApplicationContext context) {
    assertThat(context.getBean("badApple")).isNotNull();
    fail("Context should not be reachable.");
  }
  
  @Test
  @RestrictiveClassloader
  public void shoudlFailUsingAClassWithAStaticInit() throws IOException {
    try {
      assertThat(folder.newFolder()).isDirectory().canRead().canWrite();
      new StaticInitBlockClass();
      fail("Class should not have been loadable.");
    } catch (ClassFormatError cfe) {
      System.out.println("yup. bad class. life is good.");
    }
  }
  
}
