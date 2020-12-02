package io.mosip.test.packetcreator.mosippacketcreator.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import static io.restassured.RestAssured.given;

@Component
public class APIRequestUtil {

    private static final String UNDERSCORE = "_";

    Logger logger = LoggerFactory.getLogger(APIRequestUtil.class);

    String token;
    
    String refreshToken;

    @Value("${mosip.test.regclient.userid}")
    private String operatorId;

    @Value("${mosip.test.regclient.password}")
    private String password;

    @Value("${mosip.test.regclient.clientid}")
    private String clientId;

    @Value("${mosip.test.regclient.appId}")
    private String appId;

    @Value("${mosip.test.regclient.secretkey}")
    private String secretKey;

    @Value("${mosip.test.authmanager.url}")
    private String authManagerURL;

    @Value("${mosip.test.regclient.centerid}")
    private String centerId;

    @Value("${mosip.test.regclient.machineid}")
    private String machineId;


    final String dataKey = "response";
    final String errorKey = "errors";
    
    public JSONObject get(String url, JSONObject requestParams, JSONObject pathParam) throws Exception {
        //TODO: handle the expiry
        if (null == token){
            initToken();
        }
        Cookie kukki = new Cookie.Builder("Authorization", token).build();
        Response response = given().cookie(kukki).contentType(ContentType.JSON).queryParams(requestParams.toMap()).get(url,pathParam.toMap());

        checkErrorResponse(response.getBody().asString());

        return new JSONObject(response.getBody().asString()).getJSONObject(dataKey);
    }


    public JSONObject post(String url, JSONObject jsonRequest) throws Exception {
        //TODO: handle the expiry
        if (null == token){
            initToken();
        }
        /*JSONObject requestBody = new JSONObject();
       // requestBody.put("metadata", "");
        requestBody.put("version", "1.0");
        requestBody.put("id", "mosip.pre-registration.datasync.fetch.ids");
        requestBody.put("requesttime", getUTCDateTime(null));
        requestBody.put("request", jsonRequest);*/
        Cookie kukki = new Cookie.Builder("Authorization", token).build();
        Response response = given().cookie(kukki).contentType(ContentType.JSON).body(jsonRequest.toString()).post(url);

        checkErrorResponse(response.getBody().asString());

        return new JSONObject(response.getBody().asString()).getJSONObject(dataKey);
    }


    public JSONArray syncRid(String url, String requestBody, String timestamp) throws Exception {
        //TODO: handle the expiry
        if (null == token){
            initToken();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        String outputJson = objectMapper.writeValueAsString(requestBody);

        Cookie kukki = new Cookie.Builder("Authorization", token).build();
        Response response = given().cookie(kukki)
                .header("timestamp", timestamp)
                .header("Center-Machine-RefId", centerId + UNDERSCORE + machineId)
                .contentType(ContentType.JSON).body(outputJson).post(url);

        checkErrorResponse(response.getBody().asString());

        return new JSONObject(response.getBody().asString()).getJSONArray(dataKey);
    }

    @PostConstruct
    public boolean initToken(){
        try {		
			JSONObject requestBody = new JSONObject();
			JSONObject nestedRequest = new JSONObject();
			nestedRequest.put("userName", operatorId);
			nestedRequest.put("password", password);
            nestedRequest.put("appId", appId);
            nestedRequest.put("clientId", clientId);
            nestedRequest.put("clientSecret", secretKey);
			requestBody.put("metadata", "");
			requestBody.put("version", "1.0");
			requestBody.put("id", "test");
			requestBody.put("requesttime", getUTCDateTime(LocalDateTime.now()));
			requestBody.put("request", nestedRequest);

            //authManagerURL
            //String AUTH_URL = "v1/authmanager/authenticate/internal/useridPwd";
            Response response = given().contentType("application/json").body(requestBody.toString()).post(authManagerURL);
			logger.info("Authtoken generation request response: {}", response.asString());

            if (response.toString().contains("errorCode")) return false;
            
            token = new JSONObject(response.getBody().asString()).getJSONObject(dataKey).getString("token");
            refreshToken = new JSONObject(response.getBody().asString()).getJSONObject(dataKey).getString("refreshToken");

			return true;	
		}
		catch(Exception  ex){
            logger.error("",ex);
            return false;
		}
        //https://dev.mosip.net/v1/authmanager/authenticate/internal/useridPwd
    }

    /**
     * 
     * @param time nullable send null if you need current time.
     * @return the date as string as used by our request api.
     */
    public String getUTCDateTime(LocalDateTime time) {
		String DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(DATEFORMAT);
        if (time == null){
            time = LocalDateTime.now(TimeZone.getTimeZone("UTC").toZoneId());
        }  
		String utcTime = time.format(dateFormat);
		return utcTime;
    }
    
    /**
     * 
     * @param date nullable send null if you need current time.
     * @return the date as string as used by our request api.
     */
    public String getUTCDate(LocalDateTime date) {
		String DATEFORMAT = "yyyy-MM-dd";
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(DATEFORMAT);
        if (date == null){
            date = LocalDateTime.now(TimeZone.getTimeZone("UTC").toZoneId());
        }  
		String utcTime = date.format(dateFormat);
		return utcTime;
	}

	private void checkErrorResponse(String response) throws Exception {
        //TODO: Handle 401 or token expiry
        JSONObject jsonObject =  new JSONObject(response);

        if(jsonObject.has(errorKey) && jsonObject.get(errorKey) != JSONObject.NULL) {
            throw new Exception(String.valueOf(jsonObject.get(errorKey)));
        }
    }
    
}
