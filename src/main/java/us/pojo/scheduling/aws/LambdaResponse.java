package us.pojo.scheduling.aws;

import java.util.HashMap;
import java.util.Map;

public class LambdaResponse {
	private boolean isBase64Encoded = false;
	private int statusCode = 200;
	private Map<String, String> headers = new HashMap<>();
	private Result body;
	
	public LambdaResponse() {
		
	}
	
	public LambdaResponse(Result body) {
		this.body = body;
	}
	
	public boolean isBase64Encoded() {
		return isBase64Encoded;
	}
	public void setBase64Encoded(boolean isBase64Encoded) {
		this.isBase64Encoded = isBase64Encoded;
	}
	public int getStatusCode() {
		return statusCode;
	}
	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}
	public Map<String, String> getHeaders() {
		return headers;
	}
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}
	public Result getBody() {
		return body;
	}
	public void setBody(Result body) {
		this.body = body;
	}
	
	
}
