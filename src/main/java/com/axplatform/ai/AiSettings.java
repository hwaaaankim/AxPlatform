package com.axplatform.ai;

import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.axplatform.common.Problem;

/** Only api-key is secret; nonsecret defaults are committed and can be overridden privately. */
@Component
public final class AiSettings {
    private static final String PREFIX="ax.integrations.openai.";
    private final Environment env;
    public AiSettings(Environment env){this.env=env;}
    private String value(String key,String fallback){
        String value=env.getProperty(PREFIX+key,fallback).strip();
        return value.isEmpty()&&!"api-key".equals(key)?fallback:value;
    }
    private int integer(String key,int fallback,int min,int max){
        int value=env.getProperty(PREFIX+key,Integer.class,fallback);
        if(value<min||value>max)throw new Problem(503,"AI_CONFIGURATION","OpenAI 설정 범위를 확인해 주세요: "+key);
        return value;
    }
    public boolean enabled(){return env.getProperty(PREFIX+"enabled",Boolean.class,true);}
    public String key(){return value("api-key","");}
    public boolean supplied(){return !key().isBlank()&&!key().startsWith("CHANGE_ME");}
    public String model(){String m=value("model","gpt-6-astra");if(!m.matches("[a-zA-Z0-9._:-]{1,100}"))throw new Problem(503,"AI_CONFIGURATION","모델 설정을 확인해 주세요.");return m;}
    public String reasoning(){String r=value("reasoning-effort","high");if(!Set.of("low","medium","high","xhigh","max").contains(r))throw new Problem(503,"AI_CONFIGURATION","reasoning-effort 설정을 확인해 주세요.");return r;}
    public int outputTokens(){return integer("max-output-tokens",16000,2000,32768);}
    public int requestSeconds(){return integer("request-timeout-seconds",120,10,240);}
    public int rounds(){return integer("max-tool-rounds",6,1,10);}
    public int assets(){return integer("max-assets",3,0,3);}
    public int daily(){return integer("max-daily-runs",30,1,500);}
    public int concurrent(){return integer("max-concurrent-runs",2,1,4);}
    public int threads(){return integer("worker-threads",2,1,4);}
    public int jobSeconds(){return integer("job-timeout-seconds",300,30,600);}
    public void require(){if(!enabled()||!supplied())throw new Problem(503,"AI_NOT_CONFIGURED","프로젝트 루트 config/application-private.yml의 ax.integrations.openai.api-key를 입력하고 AI 사용을 활성화해 주세요.");model();reasoning();outputTokens();}
    public Map<String,Object> publicSettings(){
        boolean available=enabled()&&supplied();
        return Map.of("available",available,"model",model(),"reasoningEffort",reasoning(),"reason",available?"서버에서 호출합니다. 검토 후 선택 적용하며 API 사용료가 발생할 수 있습니다.":"개인 설정파일의 OpenAI API 키를 입력한 뒤 앱을 다시 시작해 주세요.","limits",Map.of("maxAssets",assets(),"maxAssetBytes",6*1024*1024,"maxPromptLength",12000,"maxDailyRuns",daily(),"maxConcurrentRuns",concurrent(),"maxToolRounds",rounds(),"jobTimeoutSeconds",jobSeconds()));
    }
}
