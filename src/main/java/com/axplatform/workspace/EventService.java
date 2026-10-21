package com.axplatform.workspace;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.policy.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
/** Events contain scoped metadata only. Slow network writes never hold the global policy lock. */
@Service public class EventService {
    private record Client(SessionSubject subject,long business,long workspace,SseEmitter emitter,long[] cursor,long[] revision,Set<String> previousCapabilities,jakarta.servlet.http.HttpSession lease,String sessionId) {
    }
    private record Batch(long revision,Set<String> capabilities,List<Map<String,Object>> events) {
    }
    private final java.util.concurrent.Semaphore capacity=new java.util.concurrent.Semaphore(100);
    private final Set<Client> clients=ConcurrentHashMap.newKeySet();
    private final JdbcTemplate db;
    private final AccessService access;
    private final PolicyService policies;
    public EventService(JdbcTemplate db,AccessService access,PolicyService policies) {
        this.db=db;
        this.access=access;
        this.policies=policies;
    }
    public SseEmitter subscribe(SessionSubject s,long business,String last,jakarta.servlet.http.HttpSession lease) {
        String originalSessionId=SessionLease.captureId(lease,s);
        long cursor=0;
        try {
            if(last!=null&&!last.isBlank())cursor=Long.parseLong(last);
        }
        catch(NumberFormatException e) {
            throw Problem.invalid("이벤트 식별값을 확인해 주세요.");
        }
        if(cursor<0)throw Problem.invalid("이벤트 식별값을 확인해 주세요.");
        final long start=cursor;
        Client client=access.business(s,business,true,List.of("workspace.events.read"),()-> {
            var scope=access.scope(s,business,true);
            long workspace=Inputs.number(scope,"workspace_id");
            var emitter=new SseEmitter(30L*60*1000);
            long max=db.queryForObject("SELECT COALESCE(MAX(id),0) FROM ap_event_outbox WHERE workspace_id=?",Long.class,workspace);
            return new Client(s,business,workspace,emitter,new long[] {
                start==0||start>max?max:start
            }
            ,new long[] {
                -1
            }
            ,new HashSet<>(),lease,originalSessionId);
        }
        );
        if(!capacity.tryAcquire())throw new Problem(503,"EVENT_CAPACITY","실시간 연결이 많습니다. 잠시 후 다시 시도해 주세요.");
        clients.add(client);
        Runnable end=()->remove(client);
        client.emitter().onCompletion(end);
        client.emitter().onTimeout(()-> {
            remove(client);
            client.emitter().complete();
        }
        );
        client.emitter().onError(e->remove(client));
        try {
            send(client,"ready",Map.of("resync",true,"businessId",business),null);
        }
        catch(RuntimeException e) {
            end.run();
            client.emitter().complete();
        }
        return client.emitter();
    }
    @Scheduled(fixedDelay=1500) public void poll() {
        for(Client c:clients) {
            try {
                SessionLease.require(c.lease(),c.subject(),c.sessionId());
                Batch batch=access.business(c.subject(),c.business(),true,List.of("workspace.events.read"),()->new Batch(Inputs.number(policies.state(false),"revision"),access.allowed(c.subject(),c.workspace()),db.queryForList("SELECT id,event_type,object_id,object_version FROM ap_event_outbox WHERE workspace_id=? AND (business_scope_id IS NULL OR business_scope_id=?) AND id>? ORDER BY id LIMIT 200",c.workspace(),c.business(),c.cursor()[0])));
                if(c.revision()[0]!=batch.revision()||!c.previousCapabilities().equals(batch.capabilities())) {
                    Set<String> current=access.business(c.subject(),c.business(),true,List.of("workspace.events.read"),()->access.allowed(c.subject(),c.workspace()));
                    send(c,"policy",Map.of("revision",batch.revision(),"capabilities",current),null);
                    c.revision()[0]=batch.revision();
                    c.previousCapabilities().clear();
                    c.previousCapabilities().addAll(current);
                }
                for(var event:batch.events()) {
                    long id=Inputs.number(event,"id");
                    String type=(String)event.get("event_type");
                    String permission=type.startsWith("sheet.")?"workspace.sheet.read":type.startsWith("ai.")?"workspace.ai.read":type.startsWith("draft.")?"workspace.draft.list":type.startsWith("asset.")?"workspace.asset.list":type.startsWith("scope.")?"workspace.scope.read":"workspace.dashboard.read";
                    Set<String> current=access.business(c.subject(),c.business(),true,List.of("workspace.events.read"),()->access.allowed(c.subject(),c.workspace()));
                    if(current.contains(permission))send(c,"change",event,String.valueOf(id));
                    c.cursor()[0]=id;
                }
                send(c,"heartbeat",Map.of("connected",true),null);
            }
            catch(Exception e) {
                try {
                    send(c,"revoked",Map.of("message","접속 범위·권한을 다시 확인해 주세요."),null);
                }
                catch(RuntimeException ignored) {
                }
                remove(c);
                c.emitter().complete();
            }
        }
    }
    private void remove(Client client) { if(clients.remove(client))capacity.release(); }
    private void send(Client c,String name,Object data,String id) {
        SessionLease.require(c.lease(),c.subject(),c.sessionId());
        try {
            var event=SseEmitter.event().name(name).data(data);
            if(id!=null)event.id(id);
            c.emitter().send(event);
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
