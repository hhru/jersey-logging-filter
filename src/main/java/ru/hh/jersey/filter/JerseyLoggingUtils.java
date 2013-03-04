package ru.hh.jersey.filter;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.commons.lang.StringUtils;

public class JerseyLoggingUtils {
  private JerseyLoggingUtils() { }

  public static String getRequestPath(URI uri) {
    if (uri == null) {
      return "bad request path";
    }

    return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
  }

  public static String getSummaryFromClientResponse(ClientResponse r, Set<String> maskedHeaders, final String logHeadersNameSubstr) {
    Preconditions.checkNotNull(r, "ClientResponse must nor be null");

    String httpStatusSummary = String.format("HTTP status is \"%d %s\"", r.getStatus(), r.getClientResponseStatus().getReasonPhrase());

    Map<String, List<String>> headers;
    if (StringUtils.isNotBlank(logHeadersNameSubstr)) {
      headers = Maps.filterKeys(
        r.getHeaders(),
        new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return input != null && input.contains(logHeadersNameSubstr);
          }
        });
    } else {
      headers = r.getHeaders();
    }

    String headersSummary = getHeadersSummary(headers, maskedHeaders);

    return StringUtils.join(newArrayList(httpStatusSummary, headersSummary), ", ");
  }

  public static String getSummaryFromClientResponse(ClientResponse r) {
    return getSummaryFromClientResponse(r, null, null);
  }

  @SuppressWarnings("unchecked")
  public static String getHeadersSummary(Map<?, ?> headers, Set<String> restrictedHeaders) {
    List<String> headerStrings = newArrayList();
    for (Map.Entry kv : headers.entrySet()) {
      String headerValue =
        restrictedHeaders != null && restrictedHeaders.contains(kv.getKey()) ? "***" : StringUtils.join((Collection) kv.getValue(), ',');
      headerStrings.add(String.format("header \"%s\"=\"%s\"", kv.getKey(), headerValue));
    }

    return StringUtils.join(headerStrings, ", ");
  }

  public static MultivaluedMap<String, String> convertQueryParamsToMap(String query) {
    if (StringUtils.isBlank(query)) {
      return new MultivaluedMapImpl();
    }

    MultivaluedMap<String, String> resultMap = new MultivaluedMapImpl();
    String[] paramTokens = query.split("&");
    for (String paramToken : paramTokens) {
      String[] param = paramToken.split("=");
      String paramValue = param.length > 1 ? param[1] : "";
      resultMap.add(param[0], paramValue);
    }
    return resultMap;
  }
}
