package org.ats.common.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 */

public class HttpURL {
  
  private String protocol;

  private String host;

  private String domain;
  
  private int port = -1;
  
  private String path;
  
  private String ref;

  private Map<String, String> query;

  public HttpURL(String url) throws UnsupportedEncodingException {
    this(url, null);
  }

  public HttpURL(String url, String ignoreParam) throws UnsupportedEncodingException {
    query = new LinkedHashMap<String, String>();

    String string = url.trim().toLowerCase();

    int idx = string.indexOf("://");
    if (idx > 0) {
      this.protocol = string.substring(0, idx);
      string = string.substring(idx + 3, string.length());
    }

    idx = string.indexOf('/');

    String qpath = null;
    if (idx > 0) {
      qpath = string.substring(idx, string.length());
      string = string.substring(0, idx);
    }

    int questionMark = string.indexOf('?');
    if (questionMark > 0) {
      qpath = string.substring(questionMark, string.length());
      string = string.substring(0, questionMark);
    }

    if (string.startsWith("www")) {
      if (string.length() > 3 && string.charAt(3) != '.') {
        idx = string.indexOf('.');
        this.domain = string;
        this.host = string.substring(idx + 1, string.length());
      }
    }
    
    if (string.startsWith("www.")) {
      this.host = string.substring(string.indexOf("www.") + 4);
      this.domain = string;
    }
    
    idx = string.indexOf(':');
    if (idx > 0) {
      this.port = Integer.parseInt(string.substring(idx + 1, string.length()));
      this.host = string.substring(0, idx);
    }

    if (host == null)
      host = string;
    
    if (domain == null)
      domain = host;
    
    if (port == -1) {
      if ("http".equals(protocol)) port = 80;
      else if ("https".equals(protocol)) port = 443;
    }

    if (qpath == null || qpath.length() <= 0)
      return;

    int index = qpath.indexOf('#');
    // parse ref
    ref = index < 0 ? null : qpath.substring(index + 1);
    qpath = index < 0 ? qpath : qpath.substring(0, index);

    index = qpath.indexOf('?');
    if (index > -1) {
      String queryValue = qpath.substring(index + 1);

      String[] elements = queryValue.split("\\&");
      for (String element : elements) {
        if (element.length() == 0)
          continue;
        String name = URLEncoder.encode(element.substring(0, element.indexOf('=')), "UTF-8");
        String value = URLEncoder.encode(element.substring(name.length() + 1), "UTF-8");
        query.put(name, value);
      }

      if (ignoreParam != null) {
        Iterator<String> iterator = query.keySet().iterator();
        while (iterator.hasNext()) {
          String param = iterator.next();
          if (param.startsWith(ignoreParam))
            iterator.remove();
        }
      }

      path = parse(qpath.substring(0, index), '/');
    } else {
      path = parse(qpath, '/');
    }
  }

  private String parse(String value, char separator) {
    int index = 1;

    while (index < value.length()) {
      int end = value.indexOf(separator, index);
      if (end < 0)
        return value;

      if (end == index) {
        index = end + 1;
        continue;
      }

      String pattern = value.substring(index, end);
      int newEnd = value.indexOf(pattern, end);
      if (newEnd < 0) {
        index = end + 1;
        continue;
      }

      pattern = value.substring(index, newEnd);
      while (count(value, pattern) >= 3 && newEnd < value.length()) {
        value = value.substring(0, index)
            + value.substring(Math.min(newEnd, value.length()));
      }
      index = end + 1;
    }

    return value;
  }

  private int count(String value, String pattern) {
    int count = 0;
    int start = 0;

    while (start < value.length()) {
      int index = value.indexOf(pattern, start);
      if (index < 0) {
        return count;
      }
      start = index + pattern.length();
      count++;
    }
    return count;
  }

  public String getProtocol() {
    return protocol;
  }

  public String getHost() {
    return host;
  }
  
  public String getDomain() {
    return domain;
  }

  public int getPort() {
    return port;
  }

  public String getRef() {
    return ref;
  }
  
  public String getPath() {
    return path == null ? path  = "/" : path;
  }
  
  public String getFullPath() {
    
    if (this.path == null) return null;
    
    StringBuilder sb = new StringBuilder(this.path);
   if (query.size() > 0) {
     sb.append('?');
     Iterator<Map.Entry<String, String>> i = query.entrySet().iterator();
     while(i.hasNext()) {
       Map.Entry<String, String> entry = i.next();
       sb.append(entry.getKey()).append("=").append(entry.getValue());
       if (i.hasNext()) sb.append('&');
     }
   }
   if (ref != null && !ref.isEmpty()) sb.append("#").append(ref); 
    return sb.toString();
  }
  
  public String getQueryString() {
    StringBuilder sb = new StringBuilder();
    for (Iterator<Map.Entry<String, String>> i = query.entrySet().iterator(); i.hasNext();) {
      Map.Entry<String, String> entry = i.next();
      sb.append(entry.getKey()).append("=").append(entry.getValue());
      if (i.hasNext()) sb.append("&");
    }
    return sb.toString();
  }
  
  public Map<String, String> getQueryParameters() {
    return Collections.unmodifiableMap(query);
  }

  public String getNormalizeURL() {
    StringBuilder b = new StringBuilder();
    b.append(this.protocol).append("://").append(this.domain);

    if (this.port != 80) {
      b.append(':').append(this.port);
    }

    if (path != null)
      b.append(path);

    b.append("?").append(getQueryString());

    return b.toString();
  }

  public static String getSource(String url) {
    int idx1 = url.indexOf("://");
    if (idx1 < 1)
      idx1 = 0;
    else
      idx1 += 3;

    int idx2 = url.indexOf('/', idx1);
    if (idx2 < 0)
      idx2 = url.indexOf('?');
    if (idx2 < 0)
      idx2 = url.length();

    String source = url.substring(idx1, idx2);
    source = source.toLowerCase();
    if (source.startsWith("www")) {
      int dotIdx = source.indexOf('.');
      source = source.substring(dotIdx + 1);
    }
    return source;
  }
}
