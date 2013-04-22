package ru.hh.jersey.filter;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.collect.Multimap;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.representation.Form;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JerseyLoggingFilter extends ClientFilter {
  private static final Logger log = LoggerFactory.getLogger(JerseyLoggingFilter.class);

  public static final int DEFAULT_LOWER_BOUND_INVALID_RESPONSE_CODE = 400;
  private int lowerBoundInvalidResponseCode = DEFAULT_LOWER_BOUND_INVALID_RESPONSE_CODE;

  private Set<String> restrictedHeaders = new HashSet<String>();
  private Set<String> restrictedParams = new HashSet<String>();
  private String logHeadersNameSubstr;

  public JerseyLoggingFilter() { }

  public JerseyLoggingFilter(int lowerBoundInvalidResponseCode) {
    this(lowerBoundInvalidResponseCode, Collections.<String>emptySet(), Collections.<String>emptySet(), null);
  }

  public JerseyLoggingFilter(String logHeadersNameSubstr) {
    this(DEFAULT_LOWER_BOUND_INVALID_RESPONSE_CODE, Collections.<String>emptySet(), Collections.<String>emptySet(), logHeadersNameSubstr);
  }

  public JerseyLoggingFilter(int lowerBoundInvalidResponseCode, String logHeadersNameSubstr) {
    this(lowerBoundInvalidResponseCode, Collections.<String>emptySet(), Collections.<String>emptySet(), logHeadersNameSubstr);
  }

  public JerseyLoggingFilter(Set<String> restrictedHeaders, Set<String> restrictedParams) {
    this(DEFAULT_LOWER_BOUND_INVALID_RESPONSE_CODE, restrictedHeaders, restrictedParams, null);
  }

  public JerseyLoggingFilter(Set<String> restrictedHeaders, Set<String> restrictedParams, String logHeadersNameSubstr) {
    this(DEFAULT_LOWER_BOUND_INVALID_RESPONSE_CODE, restrictedHeaders, restrictedParams, logHeadersNameSubstr);
  }

  public JerseyLoggingFilter(
      int lowerBoundInvalidResponseCode, Set<String> restrictedHeaders, Set<String> restrictedParams, String logHeadersNameSubstr) {
    this.lowerBoundInvalidResponseCode = lowerBoundInvalidResponseCode;
    this.restrictedHeaders.addAll(restrictedHeaders);
    this.restrictedParams.addAll(restrictedParams);
    this.logHeadersNameSubstr = logHeadersNameSubstr;
  }

  @Override
  public ClientResponse handle(ClientRequest clientRequest) throws ClientHandlerException {
    ClientResponse clientResponse = getNext().handle(clientRequest);
    if (clientResponse.getStatus() < lowerBoundInvalidResponseCode) {
      return clientResponse;
    }

    URI uri = clientRequest.getURI();
    try {
      List<MultivaluedMap<String, String>> paramsList = new ArrayList<MultivaluedMap<String, String>>();

      paramsList.add(JerseyLoggingUtils.convertQueryParamsToMap(uri.getQuery()));

      Object rawEntity = clientRequest.getEntity();
      if (rawEntity != null && rawEntity instanceof Form) {
        Form postParams = (Form) rawEntity;
        paramsList.add(postParams);
      }

      Map<String, Collection<String>> mergedParams = mergeParams(paramsList);
      String paramsSummary = convertParamsMapToMaskedString(mergedParams, restrictedParams);

      String headers = JerseyLoggingUtils.getHeadersSummary(clientRequest.getHeaders(), restrictedHeaders);
      String paramsAndHeaders = StringUtils.join(newArrayList(paramsSummary, headers), ", ");

      log.warn(
        "Request to \"" + JerseyLoggingUtils.getRequestPath(uri) + "\" " + paramsAndHeaders
        + " has returned " + JerseyLoggingUtils.getSummaryFromClientResponse(clientResponse, restrictedHeaders, logHeadersNameSubstr));
    } catch (Exception e) {
      log.warn("Can't log response from " + JerseyLoggingUtils.getRequestPath(uri) + ", exception message: " + e.getMessage());
    }
    return clientResponse;
  }

  private Map<String, Collection<String>> mergeParams(List<MultivaluedMap<String, String>> allParams) {
    if (allParams == null) {
      return Collections.emptyMap();
    }

    Multimap<String, String> unwoundParams = HashMultimap.create();
    for (MultivaluedMap<String, String> paramMap : allParams) {
      for (Map.Entry<String, List<String>> paramEntry : paramMap.entrySet()) {
        for (String value : paramEntry.getValue()) {
          unwoundParams.put(paramEntry.getKey(), value);
        }
      }
    }

    return unwoundParams.asMap();
  }

  private String convertParamsMapToMaskedString(Map<String, Collection<String>> postParams, final Set<String> restrictedParamNames) {
    if (postParams.isEmpty()) {
      return "without params";
    }

    MultivaluedMap<String, String> maskedParams = maskRestrictedParams(postParams, restrictedParamNames);

    Iterable<String> paramsStrings = Iterables.transform(
      maskedParams.entrySet(),
      new Function<Map.Entry<String, List<String>>, String>() {
        @Override
        public String apply(Map.Entry<String, List<String>> input) {
          return input != null ? "\"" + input.getKey() + "\"=\"" + StringUtils.join(input.getValue(), ',') + "\"" : "";
        }
      });

    return "with params " + Joiner.on(", ").join(paramsStrings);
  }

  private MultivaluedMap<String, String> maskRestrictedParams(Map<String, Collection<String>> params, Set<String> restrictedParams) {
    MultivaluedMap<String, String> maskedParams = new MultivaluedMapImpl();
    for (Map.Entry<String, Collection<String>> paramEntry : params.entrySet()) {
      for (String paramValue : paramEntry.getValue()) {
        String maskedValue = restrictedParams.contains(paramEntry.getKey()) ? "***" : paramValue;
        maskedParams.add(paramEntry.getKey(), maskedValue);
      }
    }
    return maskedParams;
  }
}
