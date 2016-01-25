package com.akamai.edgegrid.auth.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;

import org.apache.http.impl.client.HttpClientBuilder;


import com.akamai.edgegrid.auth.AkamaiConstants;
import com.akamai.edgegrid.auth.AkamaiPostData;
import com.akamai.edgegrid.auth.ApacheEdgeGridV1Signer;
import com.akamai.edgegrid.auth.EdgeGridSigner;
import com.akamai.edgegrid.auth.ClientCredential;
import com.akamai.edgegrid.auth.DefaultCredential;


public class CCUV3Test {

	
	public static void main(String args[]){
		System.out.println("Test");
		try {
			testReadApache();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
    public static void testReadApache() {

		BufferedReader        breader     = null;
        String                responseStr = null;
        final StringBuilder responseString = new StringBuilder();
		
		try{
        //For CPCode
        //URI uri = new URI(AkamaiConstants.OPEN_AUTH_SERVICE_URL + AkamaiConstants.ARL_CPCODE_STAGE_PURGING);
		
		//For URI	
		URI uri = new URI(AkamaiConstants.OPEN_AUTH_SERVICE_URL + AkamaiConstants.ARL_INVALIDATE_URL_PROD_PURGING);
	        
        
        final HttpPost httpPost = new HttpPost(uri);

        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Host", AkamaiConstants.HOST_HEADER);

        AkamaiPostData postData = new AkamaiPostData();
        
        List<String> purgeObjList = new ArrayList<String>(); 
        
        //For URL Purging
        purgeObjList.add("/akamaitest.html");
        purgeObjList.add("/akamai123.html");
        postData.setHostname(AkamaiConstants.SITE_HOST_NAME);
        
        //For CPCode Purging
        //purgeObjList.add("359525");
        postData.setObjects(purgeObjList);
        
        final HttpClient httpclient = HttpClientBuilder.create().build();
        
        final StringEntity jsonEntity = new StringEntity(postData.toJSON());

        httpPost.setEntity(jsonEntity);
        
        
        
        // Set Socket Time-Out and Connection Time-Out
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(5000).setConnectTimeout(5000).build();
        
        httpPost.setConfig(requestConfig);
        
        ClientCredential credential = new DefaultCredential(AkamaiConstants.CLIENT_TOKEN, AkamaiConstants.ACCESS_TOKEN, AkamaiConstants.CLIENT_SECRET);
        EdgeGridSigner signer = new ApacheEdgeGridV1Signer(Collections.EMPTY_LIST, 1024 * 2);
        HttpRequestBase signedRequest = signer.sign(httpPost, credential);
        final HttpResponse response   = httpclient.execute(signedRequest);
        
        breader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        String line = "";

        while ((line = breader.readLine()) != null) {
            responseString.append(line);
        }

        responseStr = responseString.toString();
        
        System.out.println("result::"+responseStr);
        
		}catch(Exception ex){
			System.out.println("Excpetion::" + ex.toString());
		}finally{
			
		}
        
    }
}
