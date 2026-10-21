package com.axplatform.common;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
@Component public class JsonCodec {
    private final JsonMapper mapper=JsonMapper.builder().build();
    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }
    public Map<String,Object> object(String value) {
        try {
            return mapper.readValue(value,new TypeReference<Map<String,Object>>() {
            }
            );
        }
        catch(RuntimeException e) {
            throw Problem.invalid("저장 자료의 형식을 확인해 주세요.");
        }
    }
    public Object value(String input) {
        try {
            return mapper.readValue(input,Object.class);
        }
        catch(RuntimeException e) {
            throw Problem.invalid("JSON 형식을 확인해 주세요.");
        }
    }
}
