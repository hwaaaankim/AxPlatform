package com.axplatform.common;
import java.util.Map;
public class Problem extends RuntimeException {
 private final int status; private final String code; private final Map<String,Object> details;
 public Problem(int status,String code,String message){this(status,code,message,Map.of());}
 public Problem(int status,String code,String message,Map<String,Object> details){super(message);this.status=status;this.code=code;this.details=details;}
 public int status(){return status;} public String code(){return code;} public Map<String,Object> details(){return details;}
 public static Problem invalid(String message){return new Problem(400,"INVALID_INPUT",message);}
 public static Problem conflict(String message){return new Problem(409,"VERSION_CONFLICT",message);}
}
