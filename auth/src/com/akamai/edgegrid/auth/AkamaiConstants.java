package com.akamai.edgegrid.auth;


public final class AkamaiConstants {
	
	
	 /* These credentials can be generated in Akamai Manage Users for CCUV3 Purging. Replace the below constants accordingly*/
		public static final String CLIENT_TOKEN = "SOME_CLIENT_TOKEN";
		 public static final String ACCESS_TOKEN = "SOME_ACCESS_TOKEN";
		 public static final String CLIENT_SECRET = "SOME_CLIENT_SECRET";
		 public static final String HOST_HEADER = "akab-6ttmqwzvj65du4am-wmfxfldqdvkunxty.purge.akamaiapis.net";
		 public static final String OPEN_AUTH_SERVICE_URL = "https://akab-6ttmqwzvj65du4am-wmfxfldqdvkunxty.purge.akamaiapis.net";
		 
		 /* For e.g. www.yoursite.com which is hosted on Akamai*/
		 public static final String SITE_HOST_NAME = "www.yoursite.com";
	 
		 public static final String ARL_INVALIDATE_URL_PROD_PURGING = "/ccu/v3/invalidate/url/production";
		 public static final String ARL_INVALIDATE_URL_STAGE_PURGING = "/ccu/v3/invalidate/url/staging";
		 public static final String ARL_INVALIDATE_CPCODE_STAGE_PURGING = "/ccu/v3/invalidate/cpcode/staging";
		 public static final String ARL_INVALIDATE_CPCODE_PROD_PURGING = "/ccu/v3/invalidate/cpcode/staging";
		 
		 public static final String ARL_REMOVE_URL_PROD_PURGING = "/ccu/v3/delete/url/production";
		 public static final String ARL_REMOVE_URL_STAGE_PURGING = "/ccu/v3/delete/url/staging";
		 public static final String ARL_REMOVE_CPCODE_STAGE_PURGING = "/ccu/v3/delete/cpcode/staging";
		 public static final String ARL_REMOVE_CPCODE_PROD_PURGING = "/ccu/v3/delete/cpcode/staging";
		 
	 
	
	 
	 

}
