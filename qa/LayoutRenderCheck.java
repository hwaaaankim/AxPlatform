import java.nio.file.*;
import java.util.*;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;

public class LayoutRenderCheck {
  public static void main(String[] args) throws Exception {
    Path templates=Path.of(args[0]), output=Path.of(args[1]);
    Files.createDirectories(output);
    FileTemplateResolver resolver=new FileTemplateResolver();
    resolver.setPrefix(templates.toAbsolutePath()+"/");resolver.setSuffix(".html");
    resolver.setTemplateMode("HTML");resolver.setCharacterEncoding("UTF-8");resolver.setCacheable(false);
    SpringTemplateEngine engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
    engine.setLinkBuilder(new StandardLinkBuilder(){
      @Override protected String computeContextPath(IExpressionContext context,String base,Map<String,Object> parameters){return "";}
    });
    Map<String,Object> model=new HashMap<>();
    model.put("_csrf",Map.of("token","layout-test-token","headerName","X-CSRF-TOKEN"));
    model.put("manager",false);model.put("appName","AxPlatform");model.put("admin",true);Map<String,Object> params=new HashMap<>();for(String key:List.of("error","expired","passwordChanged","socialError"))params.put(key,null);model.put("param",params);
    model.put("connections",Map.of("registration",false,"google",false,"kakao",false,"naver",false,"email",false,"sms",false));
    model.put("space",Map.of("id",1,"effective_tier","ADMIN"));model.put("showPlan",false);
    model.put("memberCount",0);model.put("scopes",List.of(Map.of("id",1)));model.put("memberId",1);
    model.put("initialized",true);model.put("migration",List.of(Map.of("version","V004","description","표·분석 보기","applied_at","2026-10-20T00:00:00")));model.put("migrations",List.of());model.put("securityVersion",1);
    model.put("result",Map.of("query","","type","CUSTOMER","status","ALL","method","ALL","number",1,"totalPages",1,"total",0,"members",List.of()));
    model.put("kind","terms");model.put("version","2026-10-09-DRAFT");model.put("resetToken","");model.put("message","요청 내용을 확인해 주세요.");
    model.put("businessId",1);model.put("workspaceId",1);model.put("workflow","EXCEL");
    int count=0;
    try(var files=Files.walk(templates)){
      for(Path file:files.filter(p->p.toString().endsWith(".html")&&!p.toString().contains("fragments")).toList()){
        String name=templates.relativize(file).toString().replace("\\","/").replace(".html","");
        String html=engine.process(name,new Context(Locale.KOREAN,model));
        Files.writeString(output.resolve(name.replace('/','_')+".html"),html);
        if(!name.startsWith("error/")){
          if(html.split("class=\"topbar\"",-1).length-1!=1)throw new IllegalStateException(name+": duplicate/missing header");
          if(html.split("class=\"brand\"",-1).length-1!=1)throw new IllegalStateException(name+": duplicate/missing brand");
        }
        count++;
      }
    }
    for(String wf:List.of("WORD","HWP","PPT","CRM","CALENDAR","APP")){
      model.put("workflow",wf);
      Files.writeString(output.resolve("workspace_draft_"+wf+".html"),engine.process("workspace/draft",new Context(Locale.KOREAN,model)));count++;
    }
    model.put("manager",true);Files.writeString(output.resolve("workspace_files_admin.html"),engine.process("workspace/files",new Context(Locale.KOREAN,model)));count++;
    model.put("admin",false);
    Files.writeString(output.resolve("workspace_dashboard_customer.html"),engine.process("workspace/dashboard",new Context(Locale.KOREAN,model)));count++;
    System.out.println("PASS: "+count+" Thymeleaf page variants rendered; each non-error page has exactly one header and one brand.");
  }
}
