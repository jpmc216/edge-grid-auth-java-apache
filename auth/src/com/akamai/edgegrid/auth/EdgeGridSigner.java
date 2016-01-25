package com.akamai.edgegrid.auth;

import org.apache.http.client.methods.HttpRequestBase;



public interface EdgeGridSigner {
	
	
	/**
	 * Signs a request with the client credential.
	 * 
	 * @param request the request to sign.
	 * @param credential the credential used in the signing.
	 * @return the signed request.
	 * @throws RequestSigningException
	 */
	public HttpRequestBase sign(HttpRequestBase request, ClientCredential credential) throws RequestSigningException;

}
