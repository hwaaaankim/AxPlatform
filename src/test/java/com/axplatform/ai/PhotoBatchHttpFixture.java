package com.axplatform.ai;

import com.axplatform.common.JsonCodec;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Flow;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** In-process HTTP fixture: sendAsync never connects to any host. */
public final class PhotoBatchHttpFixture {
    public final List<String> sheetIds=new CopyOnWriteArrayList<>();
    public final List<Long> photoCounts=new CopyOnWriteArrayList<>();
    public final AtomicInteger generations=new AtomicInteger();
    @SuppressWarnings("unchecked") public OpenAiResponses provider(AiSettings settings,JsonCodec json){
        HttpClient client=mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(inv->{
            HttpRequest request=inv.getArgument(0);var payload=json.object(body(request));Object result;
            if(request.uri().getPath().endsWith("/input_tokens"))result=Map.of("input_tokens",100);
            else {
                int call=generations.getAndIncrement();List<?> input=(List<?>)payload.get("input");
                long photos=input.stream().filter(v->v instanceof Map<?,?> m&&m.get("content") instanceof List<?>).flatMap(v->((List<?>)((Map<?,?>)v).get("content")).stream()).filter(v->v instanceof Map<?,?> m&&"input_image".equals(m.get("type"))).count();photoCounts.add(photos);
                List<?> output;
                if(call%2==0)output=List.of(function("read_workflow_guide","{}","guide"),function("read_current_draft","{}","draft"),function("read_current_sheet","{}","sheet"));
                else {
                    for(Object value:input)if(value instanceof Map<?,?> m&&"function_call_output".equals(m.get("type"))&&"sheet".equals(m.get("call_id")))sheetIds.add((String)json.object((String)m.get("output")).get("sheetId"));
                    var proposal=new LinkedHashMap<String,Object>();proposal.put("summary","사진별 검토 fixture");proposal.put("confirmed",List.of());proposal.put("assumptions",List.of());proposal.put("questions",List.of());proposal.put("warnings",List.of());proposal.put("ready",true);
                    proposal.put("draft",Map.of("title","검토용","answers",Map.of("purpose","","data","","result","","details","","audience","")));
                    proposal.put("sheet",Map.of("columns",List.of(Map.of("id","code","label","식별번호","type","TEXT","unit","","width",160)),"rows",List.of(List.of("PHOTO_"+(call/2+1))),"rowIds",List.of("r"+(call/2+1))));
                    output=List.of(function("propose_workflow",json.write(proposal),"proposal"));
                }
                result=Map.of("status","completed","output",output,"usage",Map.of("input_tokens",100,"output_tokens",20));
            }
            HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(json.write(result).getBytes(StandardCharsets.UTF_8));return CompletableFuture.completedFuture(response);
        });return new OpenAiResponses(settings,json,client);
    }
    private static Map<String,Object> function(String name,String args,String id){return Map.of("type","function_call","name",name,"arguments",args,"call_id",id);}
    private static String body(HttpRequest request){var out=new ByteArrayOutputStream();request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}public void onNext(ByteBuffer b){byte[] data=new byte[b.remaining()];b.get(data);out.writeBytes(data);}public void onComplete(){}public void onError(Throwable t){throw new AssertionError(t);}});return out.toString(StandardCharsets.UTF_8);}
}
