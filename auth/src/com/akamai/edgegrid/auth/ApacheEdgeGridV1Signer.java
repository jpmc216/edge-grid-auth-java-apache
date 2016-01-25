package com.akamai.edgegrid.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;










public class ApacheEdgeGridV1Signer implements EdgeGridSigner {

	/**
	 * The logger used for logging.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(ApacheEdgeGridV1Signer.class);
	
	/**
	 * The signing algorithm for the EdgeGrid version 1 protocol.
	 */
	private static final String ALGORHTM = "EG1-HMAC-SHA256";

	/**
	 * The HMAC algorithm used.
	 */
	private static final String HMAC_ALG = "HmacSHA256";
	
	/**
	 * The message digest algorithm used.
	 */
	private static final String MD_ALG = "SHA-256";

	/**
	 * The charset used for String to bytes conversions.
	 */
	private static final String CHARSET = "UTF-8";
	
	/**
	 * The field name for the client token in the authorization header.
	 */
	private static final String AUTH_CLIENT_TOKEN_NAME = "client_token";
		
	/**
	 * The field name for the access token in the authorization header.
	 */
	private static final String AUTH_ACCESS_TOKEN_NAME = "access_token";
		
	/**
	 * The field name for the time stamp in the authorization header.
	 */
	private static final String AUTH_TIMESTAMP_NAME = "timestamp";
		
	/**
	 * The field name for the nonce in the authorization header.
	 */
	private static final String AUTH_NONCE_NAME = "nonce";
		
	/**
	 * The field name for the signature in the authorization header.
	 */
	private static final String AUTH_SIGNATURE_NAME = "signature";
	
	/**
	 * The ordered list of header names to include in the signature.
	 */
	private final List<String> headersToInclude;
	
	/**
	 * The maximum allowed body size in bytes for POST and PUT requests.
	 */
	private final int maxBodySize;
	
	/**
	 * Constructor
	 * 
	 * <p>
	 * Note: the parameters should be published by the service provider when the service
	 * is published. Refer to the API documentation for any special instructions.
	 * </p>
	 * 
	 * @param headers the ordered list of header names to include in the signature.
	 * @param maxBodySize the maximum allowed body size in bytes for POST and PUT requests.
	 */
	public ApacheEdgeGridV1Signer(List<String> headers, int maxBodySize) {
		this.headersToInclude = headers;
		this.maxBodySize = maxBodySize;
	}
	
	/**
	 * Get the canonicalized uri.
	 * 
	 * <p>
	 * The canonicalization is done as the following:
	 * </p>
	 * 
	 * <ul>
	 * <li>
	 * If the path is null or empty, set it to "/".
	 * </li>
	 * <li>
	 * If the path does not start with "/", add "/" to the beginning.
	 * </li>
	 * </ul>
	 * 
	 * @param uri the original uri.
	 * @return the canonicalized uri.
	 */
	protected String canonicalizeUri(String uri) {
		if (Utils.isNullOrEmpty(uri)) {
			return "/";
		}
		
		if (uri.charAt(0) != '/') {
			uri = "/" + uri;
		}
		
		return uri;
	}
	
	
	/**
	 * Helper method to calculate the message digest.
	 * 
	 * @param contentBytes the content bytes for digesting.
	 * @return the digest.
	 * @throws RequestSigningException
	 */
	private static byte[] getHash(byte[] contentBytes, int offset, int len) throws RequestSigningException {
		try {
			MessageDigest md = MessageDigest.getInstance(MD_ALG);
			
			md.update(contentBytes, offset, len);
			byte[] digestBytes = md.digest();
			return digestBytes;
		} catch (NoSuchAlgorithmException nsae) {
			throw new RequestSigningException("Failed to get request hash: algorithm not found", nsae);
		}
	}
	
	/**
	 * Helper to get the formatted time stamp. 
	 * 
	 * @param time the time stamp as millisecond since the UNIX epoch.
	 * @return the formatted time stamp.
	 */
	private static String getTimeStamp(long time) {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HH:mm:ssZ");
		Date date = new Date(time);
		
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(date);
	}
	
	/**
	 * Helper method to get the host name from the request header.
	 * 
	 * @param request the request.
	 * @return host name.
	 */
	private static String getHost(HttpRequestBase request) {
		if(request.containsHeader("host")){
			return request.getFirstHeader("host").getValue();
		}
		return null;
	}
	
	
	/**
	 * Gets the canonicalized data of the given request.
	 * 
	 * <p>
	 * The canonicalized data contains the list of fields separate with a tab '\t':
	 * </p>
	 * 
	 * <ol>
	 * <li>
	 * the request method (GET/PUT etc.) in upper case;
	 * </li>
	 * <li>
	 * the scheme (http/https) in lower case;
	 * </li>
	 * <li>
	 * the host from the Host header in lower case;
	 * </li>
	 * <li>
	 * the relative URL that contains the path and query portions of the URL,
	 * as it appears in the HTTP request line, see {@link #canonicalizeUri};
	 * </li>
	 * <li>
	 * the canonicalized request headers, see {@link #canonicalizeHeaders};
	 * </li>
	 * <li>
	 * the content hash of the request body for POST requests, see {@link #getContentHash}.
	 * </li>
	 * </ol>
	 * 
	 * @param request the request.
	 * @return the canonicalized data, and the possibly updated request.
	 * @throws RequestSigningException
	 */
	protected CanonicalizerHelper getCanonicalizedRequest(HttpRequestBase request) throws RequestSigningException {
		StringBuilder sb = new StringBuilder();

		String method = request.getMethod();
		if (Utils.isNullOrEmpty(method)) {
			throw new RequestSigningException("Invalid request: empty request method");
		}
		sb.append(method.toUpperCase());
		sb.append('\t');

		URI uri = request.getURI();

		String scheme = uri.getScheme();
		if (Utils.isNullOrEmpty(scheme)) {
			throw new RequestSigningException("Invalid request: empty request scheme");
		}
		sb.append(scheme.toLowerCase());
		sb.append('\t');

		String host = getHost(request);
		if (Utils.isNullOrEmpty(host)) {
			throw new RequestSigningException("Invalid request: empty host");
		}
		sb.append(host.toLowerCase());
		sb.append('\t');

		String rawUrl = request.getURI().getRawPath();
		String relateiveUrl = canonicalizeUri(rawUrl);
		sb.append(relateiveUrl);
		sb.append('\t');

		String canonicalizedHeaders = canonicalizeHeaders(request);
		sb.append(canonicalizedHeaders);
		sb.append('\t');

		CanonicalizerHelper contentHashResult = getContentHash(request);
		String contentHash = contentHashResult.getCanonicalizedData();
		sb.append(contentHash);
		sb.append('\t');
		
		String data = sb.toString();
		
		return new CanonicalizerHelper(data, contentHashResult.getRequest());
	}
	
	
	
	/**
	 * Get the canonicalized data for the request headers.
	 * 
	 * <p>
	 * The canonicalization is done as the following:
	 * </p>
	 * 
	 * <p>
	 * For each entry in the {@link #headersToInclude},
	 * </p>
	 * 
	 * <ul>
	 * <li>
	 * get the first header value for the name;
	 * </li>
	 * <li>
	 * trim the leading and trailing white spaces;
	 * </li>
	 * <li>
	 * replace all repeated white spaces with a single space;
	 * <p>
	 * Note: the canonicalized data is used for signature only, as this step might alter the header value.
	 * </p>
	 * </li>
	 * <li>
	 * concatenate the name:value pairs with a tab '\t' separator. The name field is all in lower cases.
	 * </li>
	 * <li>
	 * terminate the headers with another tab ('\t') separator.
	 * </li>
	 * </ul>
	 * 
	 * @param request the request.
	 * @return the canonicalized data for the request headers.
	 */
	protected String canonicalizeHeaders(HttpRequestBase request) {
		StringBuilder sb = new StringBuilder();
		for (String headerName : headersToInclude) {
			// only use the first entry if more than one headers with the same name
			//String headerValue = request.getHeaders().getFirstHeaderStringValue(headerName);
			if (request.containsHeader(headerName)) {
				
				String headerValue = request.getFirstHeader(headerName).getValue();
				// trim the header value
				headerValue = headerValue.trim();

				if (!headerValue.isEmpty()) {
					Pattern p = Pattern.compile("\\s+");
					Matcher matcher = p.matcher(headerValue);
					headerValue = matcher.replaceAll(" ");

					sb.append(headerName.toLowerCase());
					sb.append(':');
					sb.append(headerValue);
					sb.append('\t');
				}
			}
		}

		return sb.toString();
	}
	
	/**
	 * Get the SHA-256 hash of the POST body.
	 * 
	 * @param request the request.
	 * @return the canonicalized data, and the possibly updated request.
	 * @throws RequestSigningException
	 */
	protected CanonicalizerHelper getContentHash(HttpRequestBase request) throws RequestSigningException {
		String data = "";
		HttpPost updatedRequestPost = null;
		
		// only do hash for POSTs for this version
		if ("POST".equalsIgnoreCase(request.getMethod())) {

			//HttpContent content = request.getContent();
			HttpPost httpPost = (HttpPost)request;
			updatedRequestPost = httpPost;
			
			HttpEntity httpContent = httpPost.getEntity();
			
			if (httpContent != null) {
				try {
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					httpContent.writeTo(bos);

					byte[] contentBytes = bos.toByteArray();

					int lengthToHash = bos.size();
					if (lengthToHash > maxBodySize) {
						LOGGER.warn(String.format(
								"Message body length '%d' is larger than the max '%d'. " +
								"Using '%d' bytes for computing the hash.", lengthToHash, maxBodySize, maxBodySize));
						lengthToHash = maxBodySize;
					} else {
						if (LOGGER.isDebugEnabled()) {
							LOGGER.debug(String.format("Content: %s", Base64.encodeBase64String(contentBytes)));
						}
					}
					
					byte[] digestBytes = getHash(contentBytes, 0, lengthToHash);
										
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug(String.format("Content hash: %s", Base64.encodeBase64String(digestBytes)));
					}
					
					//TODO
					// for non-retryable content, reset the content for downstream handlers
					/*if (!httpContent.retrySupported()) {
						HttpContent newContent = new ByteArrayContent(content.getType(), contentBytes);
						updatedRequest = request.setContent(newContent);
					}*/
					
					//TODO Need check if this is really needed
					if (!httpContent.isRepeatable()) {
						updatedRequestPost.setEntity(new ByteArrayEntity(contentBytes));
					}
					
					data = Base64.encodeBase64String(digestBytes); 
				} catch (IOException ioe) {
					throw new RequestSigningException("Failed to get content hash: failed to read content", ioe);
				}
			}
		}

		return new CanonicalizerHelper(data, updatedRequestPost);
	}
	
	/**
	 * Helper class representing the canonicalized data and possibly updated request.
	 *
	 */
	private class CanonicalizerHelper {
		
		/**
		 * The canonicalized data.
		 */
		private final String canonicalizedData;
		
		/**
		 * The request.
		 */
		private final HttpRequestBase request;
		
		/**
		 * Constructor.
		 * 
		 * @param data the canonicalized data.
		 * @param request the request.
		 */
		public CanonicalizerHelper(String data, HttpRequestBase request) {
			this.canonicalizedData = data;
			this.request = request;
		}
		
		/**
		 * Get the canonicalized data.
		 * @return the canonicalized data.
		 */
		public String getCanonicalizedData() {
			return canonicalizedData;
		}
		
		/**
		 * Get the request.
		 * @return the request.
		 */
		public HttpRequestBase getRequest() {
			return request;
		}
	}
	

	/**
	 * Signs the given request with the given client credential.
	 * 
	 * @param request the request to sign.
	 * @param credential the credential used in the signing.
	 * @return the signed request.
	 * @throws RequestSigningException
	 */
	public HttpRequestBase sign(HttpRequestBase request, ClientCredential credential) throws RequestSigningException {
		long currentTime = System.currentTimeMillis();
		String timeStamp = getTimeStamp(currentTime);
		
		StringBuilder sb = new StringBuilder();
		sb.append(ALGORHTM);
		sb.append(' ');
		sb.append(AUTH_CLIENT_TOKEN_NAME);
		sb.append('=');
		sb.append(credential.getClientToken());
		sb.append(';');
		
		sb.append(AUTH_ACCESS_TOKEN_NAME);
		sb.append('=');
		sb.append(credential.getAccessToken());
		sb.append(';');
		
		sb.append(AUTH_TIMESTAMP_NAME);
		sb.append('=');
		sb.append(timeStamp);
		sb.append(';');

		String nonce = UUID.randomUUID().toString();
		sb.append(AUTH_NONCE_NAME);
		sb.append('=');
		sb.append(nonce);
		sb.append(';');
		
		String authData = sb.toString();
		
		try {
			String clientSecret = credential.getClientSecret();
			
			byte[] signingKeyBytes = sign(timeStamp, clientSecret.getBytes(CHARSET), HMAC_ALG);
			String signingKey = Base64.encodeBase64String(signingKeyBytes);

			CanonicalizerHelper requestResult = getCanonicalizedRequest(request);
			HttpRequestBase updatedRequest = requestResult.getRequest();
			
			String requestData = requestResult.getCanonicalizedData();

			StringBuilder signData = new StringBuilder(requestData);
			signData.append(authData);

			String stringToSign = signData.toString();
			
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(String.format("String to sign : '%s'", stringToSign));
			}

			byte[] signatureBytes = sign(stringToSign, signingKey.getBytes(CHARSET), HMAC_ALG);
			String signature = Base64.encodeBase64String(signatureBytes);
			
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(String.format("Signature : '%s'", signature));
			}

			// add the signature
			sb.append(AUTH_SIGNATURE_NAME);
			sb.append('=');
			sb.append(signature);
			
			String authHeader = sb.toString();
			updatedRequest.setHeader("Authorization", authHeader);
			
			//HttpRequestBase signedRequest = updatedRequest.setHeaders(headers);
			
			return updatedRequest;
		} catch (UnsupportedEncodingException uee) {
			throw new RequestSigningException("Failed to sign: invalid string encoding", uee);
		}
	}

	
	/**
	 * Helper method to calculate the HMAC signature of a given string.
	 * 
	 * @param s the string to sign.
	 * @param key the key for the signature.
	 * @param algorithm the signing algorithm.
	 * @return the HMac signature.
	 * @throws RequestSigningException
	 */
	private static byte[] sign(String s, byte[] key, String algorithm) throws RequestSigningException {
		try {
			SecretKeySpec signingKey = new SecretKeySpec(key, algorithm);
			Mac mac = Mac.getInstance(algorithm);
			mac.init(signingKey);

			byte[] valueBytes = s.getBytes(CHARSET);
			return mac.doFinal(valueBytes);
		} catch (NoSuchAlgorithmException nsae) {
			throw new RequestSigningException("Failed to sign: algorithm not found", nsae);
		} catch (InvalidKeyException ike) {
			throw new RequestSigningException("Failed to sign: invalid key", ike);
		} catch (UnsupportedEncodingException uee) {
			throw new RequestSigningException("Failed to sign: invalid string encoding", uee);
		}
	}

}
