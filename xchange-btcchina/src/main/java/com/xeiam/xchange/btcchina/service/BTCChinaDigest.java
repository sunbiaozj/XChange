/**
 * The MIT License
 * Copyright (c) 2012 Xeiam LLC http://xeiam.com
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
package com.xeiam.xchange.btcchina.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.crypto.Mac;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import si.mazi.rescu.BasicAuthCredentials;
import si.mazi.rescu.RestInvocation;

import com.xeiam.xchange.btcchina.BTCChinaUtils;
import com.xeiam.xchange.service.BaseParamsDigest;

/**
 * @author David Yam
 */
public class BTCChinaDigest extends BaseParamsDigest {

  private static final Pattern responsePattern = Pattern.compile("\\{\"id\":([0-9]*),\"method\":\"([^\"]*)\",\"params\":\\[([^\\]]*)\\]\\}", Pattern.DOTALL | Pattern.CASE_INSENSITIVE
      | Pattern.UNICODE_CASE);

  private final Logger log = LoggerFactory.getLogger(BTCChinaDigest.class);

  private final String exchangeAccessKey;

  /**
   * Constructor
   * 
   * @param secretKeyBase64
   * @throws IllegalArgumentException if key is invalid (cannot be base-64-decoded or the decoded key is invalid).
   */
  private BTCChinaDigest(String exchangeAccessKey, String exchangeSecretKey) {

    super(exchangeSecretKey, HMAC_SHA_1);
    this.exchangeAccessKey = exchangeAccessKey;
  }

  public static BTCChinaDigest createInstance(String exchangeAccessKey, String exchangeSecretKey) {

    return exchangeSecretKey == null ? null : new BTCChinaDigest(exchangeAccessKey, exchangeSecretKey);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {

    String tonce = restInvocation.getHttpHeadersFromParams().get("Json-Rpc-Tonce");
    String requestJson = restInvocation.getRequestBody();

    String id = "", method = "", params = "";
    try {
      Matcher regexMatcher = responsePattern.matcher(requestJson);
      if (regexMatcher.find()) {
        id = regexMatcher.group(1);
        method = regexMatcher.group(2);
        params = stripParams(regexMatcher.group(3));
      }
    } catch (PatternSyntaxException ex) {
      // Syntax error in the regular expression
    }

    String signature = String.format("tonce=%s&accesskey=%s&requestmethod=%s&id=%s&method=%s&params=%s", tonce, exchangeAccessKey, "post", id, method, params);
    log.debug("signature message: {}", signature);

    Mac mac = getMac();
    byte[] hash = mac.doFinal(signature.getBytes());

    BasicAuthCredentials auth = new BasicAuthCredentials(exchangeAccessKey, BTCChinaUtils.bytesToHex(hash));

    return auth.digestParams(restInvocation);
  }

  /**
   * Strip the {@code params} for signature message.
   *
   * @param params the {@code params} in the request body.
   * @return the params string for signature message.
   * @see the note in
   *      <a href="http://btcchina.org/api-trade-documentation-en#faq">FAQ</a>
   *      4.2(USING OPENONLY AS TRUE EXAMPLE)
   */
  private String stripParams(final String params) {

    final String[] original = params.split(",");
    final String[] stripped = new String[original.length];

    for (int i = 0; i < original.length; i++) {
      final String param = original[i];

      if (param.startsWith("\"") && param.endsWith("\"")) {
        // string
        stripped[i] = param.substring(1, param.length() - 1);
      }
      else if (param.equals("true")) {
        // boolean: true
        stripped[i] = "1";
      }
      else if (param.equals("false")) {
        // boolean: false
        stripped[i] = StringUtils.EMPTY;
      }
      else {
        // number, etc.
        stripped[i] = param;
      }

    }
    return StringUtils.join(stripped, ",");
  }

}