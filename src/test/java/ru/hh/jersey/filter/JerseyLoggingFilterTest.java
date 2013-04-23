package ru.hh.jersey.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import static com.google.common.collect.Sets.newHashSet;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.ArrayList;
import java.util.Collections;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import ru.hh.jersey.test.JerseyClientTest;
import ru.hh.logback.ListAppender;

public class JerseyLoggingFilterTest extends JerseyClientTest {
  private static ListAppender listAppender;

  @BeforeClass
  public static void appendLogger() {
    listAppender = new ListAppender();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(listAppender);
    listAppender.start();
  }

  @AfterClass
  public static void stopListLogger() {
    if (listAppender != null) {
      listAppender.stop();
    }
  }

  @After
  public void resetListAppender() throws Exception {
    listAppender.list = new ArrayList<ILoggingEvent>();
  }

  @Test
  public void testShouldLogHttpStatusCode() throws Exception {
    setServerAnswer("/path", "error", 400);
    client().addFilter(new JerseyLoggingFilter());

    try {
      client().resource(getBaseURI() + "path").get(String.class);
    } catch (UniformInterfaceException e) { }

    String actualLogString = listAppender.list.get(0).getFormattedMessage();
    assertNotNull(actualLogString);
    assertTrue(actualLogString.contains("has returned HTTP status is \"400 Bad Request\""));
  }

  @Test
  public void testShouldLogParametersWhenRestrictionsWerentSet() throws Exception {
    MultivaluedMap<String, String> params = new MultivaluedMapImpl();
    params.putSingle("restrictedName", "restrictedValue");
    params.putSingle("paramName", "paramValue");
    setServerAnswer("/path", params, "error", 400);

    client().addFilter(new JerseyLoggingFilter());

    try {
      client().resource(getBaseURI() + "path").queryParams(params).get(String.class);
    } catch (UniformInterfaceException e) { }

    String actualLogString = listAppender.list.get(0).getFormattedMessage();
    assertNotNull(actualLogString);
    assertTrue(actualLogString.contains("with params \"restrictedName\"=\"restrictedValue\", \"paramName\"=\"paramValue\""));
  }

  @Test
  public void testShouldMaskRestrictedParametersWhenRestrictionsWereSet() throws Exception {
    MultivaluedMap<String, String> params = new MultivaluedMapImpl();
    params.putSingle("restrictedName", "restrictedValue");
    params.putSingle("paramName", "paramValue");
    setServerAnswer("/path", params, "error", 400);

    client().addFilter(new JerseyLoggingFilter(Collections.<String>emptySet(), newHashSet("restrictedName")));

    try {
      client().resource(getBaseURI() + "path").queryParams(params).get(String.class);
    } catch (UniformInterfaceException e) { }

    String actualLogString = listAppender.list.get(0).getFormattedMessage();
    assertNotNull(actualLogString);
    assertTrue(actualLogString.contains("with params \"restrictedName\"=\"***\", \"paramName\"=\"paramValue\""));
  }

  @Test
  public void testShouldLogHeadersWhenRestrictionsWerentSet() throws Exception {
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("restrictedHeaderName", "restrictedValue");
    headers.putSingle("headerName", "headerValue");
    setServerAnswer("/path", new MultivaluedMapImpl(), "error", 400, headers, "plain/text");

    client().addFilter(new JerseyLoggingFilter());

    try {
      client().resource(getBaseURI() + "path").get(String.class);
    } catch (UniformInterfaceException e) { }

    String actualLogString = listAppender.list.get(0).getFormattedMessage();
    assertNotNull(actualLogString);
    assertTrue(actualLogString.contains("header \"restrictedHeaderName\"=\"restrictedValue\""));
    assertTrue(actualLogString.contains("header \"headerName\"=\"headerValue\""));
  }

  @Test
  public void testShouldMaskRestrictedHeadersWhenRestrictionsWereSet() throws Exception {
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("restrictedHeaderName", "restrictedValue");
    headers.putSingle("headerName", "headerValue");
    setServerAnswer("/path", new MultivaluedMapImpl(), "error", 400, headers, "plain/text");

    client().addFilter(new JerseyLoggingFilter(newHashSet("restrictedHeaderName"), Collections.<String>emptySet()));

    try {
      client().resource(getBaseURI() + "path").get(String.class);
    } catch (UniformInterfaceException e) { }

    String actualLogString = listAppender.list.get(0).getFormattedMessage();
    assertNotNull(actualLogString);
    assertTrue(actualLogString.contains("header \"restrictedHeaderName\"=\"***\""));
    assertTrue(actualLogString.contains("header \"headerName\"=\"headerValue\""));
  }

  @Test
  public void testShouldLogOnlyHeadersWithSpecialSubstringInNameWhenSubstringWereSet() throws Exception {
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("anotherHeader", "anotherValue");
    headers.putSingle("XHH-headerName", "headerValue");
    setServerAnswer("/path", new MultivaluedMapImpl(), "error", 400, headers, "plain/text");

    client().addFilter(new JerseyLoggingFilter("XHH-"));

    try {
      client().resource(getBaseURI() + "path").get(String.class);
    } catch (UniformInterfaceException e) { }

    String actualLogString = listAppender.list.get(0).getFormattedMessage();
    assertNotNull(actualLogString);
    assertTrue(actualLogString.contains("header \"XHH-headerName\"=\"headerValue\""));
    assertFalse(actualLogString.contains("header \"anotherHeader\"=\"anotherValue\""));
  }

  @Test
  public void testShouldntLogWhenStatusCodeLessThanSpecified() throws Exception {
    setServerAnswer("/path", "error", 400);

    client().addFilter(new JerseyLoggingFilter(500));

    try {
      client().resource(getBaseURI() + "path").get(String.class);
    } catch (UniformInterfaceException e) { }

    assertTrue(listAppender.list.isEmpty());
  }

  @Test
  public void testShouldLogWhenStatusCodeMoreOrEqualThanSpecified() throws Exception {
    setServerAnswer("/path", "error", 500);

    client().addFilter(new JerseyLoggingFilter(500));

    try {
      client().resource(getBaseURI() + "path").get(String.class);
    } catch (UniformInterfaceException e) { }

    String actualLogString = listAppender.list.get(0).getFormattedMessage();
    assertNotNull(actualLogString);

    assertTrue(actualLogString.contains("has returned HTTP status is \"500 Internal Server Error\""));
  }
}
