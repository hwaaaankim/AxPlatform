package com.axplatform.web;

import com.axplatform.security.*;
import com.axplatform.sheet.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class DataCenterController {
 private final DataCenterService data;private final SheetService sheets;
 public DataCenterController(DataCenterService data,SheetService sheets){this.data=data;this.sheets=sheets;}
 public record Query(Map<String,Object> config,int page,Boolean profile){}
 public record Column(Map<String,Object> spec,long sheetVersion,long draftVersion,Boolean apply,Boolean acceptErrors){}
 public record Export(Map<String,Object> config,String format,long sheetVersion){}
 @PostMapping("/api/drafts/{id}/sheet/data") public Object query(@PathVariable String id,@RequestBody Query query){return data.data(Subjects.current(),id,query.config(),query.page(),Boolean.TRUE.equals(query.profile()));}
 @PostMapping("/api/drafts/{id}/sheet/calculated-column") public Object column(@PathVariable String id,@RequestBody Column input){return data.column(Subjects.current(),id,input.spec(),input.sheetVersion(),input.draftVersion(),Boolean.TRUE.equals(input.apply()),Boolean.TRUE.equals(input.acceptErrors()));}
 @PostMapping("/api/drafts/{id}/sheet/data-export") public ResponseEntity<StreamingResponseBody> export(@PathVariable String id,@RequestBody Export input,HttpServletRequest request){var actor=Subjects.current();var session=request.getSession(false);String sessionId=SessionLease.captureId(session,actor);var result=data.export(actor,id,input.config(),input.format(),input.sheetVersion());StreamingResponseBody stream=out->{for(int at=0;at<result.data().length;at+=32768){SessionLease.require(session,actor,sessionId);data.checkExport(actor,id,input.config());out.write(result.data(),at,Math.min(32768,result.data().length-at));}};return ResponseEntity.ok().contentType(MediaType.parseMediaType(input.format().equals("csv")?"text/csv;charset=UTF-8":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).contentLength(result.data().length).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.filename(),StandardCharsets.UTF_8).build().toString()).body(stream);}
}
