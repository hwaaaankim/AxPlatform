package com.axplatform;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.policy.PermissionRules;
import com.axplatform.workspace.AssetValidation;
/** Runs against production pure classes, independently of Spring or a database. */
public final class P1CoreAssertions {
    private static int checks;
    private static void check(boolean ok,String message) {
        checks++;
        if(!ok)throw new AssertionError(message);
    }
    private static void reject(Runnable r,String label) {
        checks++;
        try {
            r.run();
            throw new AssertionError(label);
        }
        catch(IllegalArgumentException|Problem expected) {
        }
    }
    private static Map<String,List<String>> grants() {
        Map<String,List<String>> out=new LinkedHashMap<>();
        for(String tier:PermissionRules.TIERS)out.put(tier,new ArrayList<>());
        return out;
    }
    private static void badFile(String name,byte[] bytes)throws Exception {
        checks++;
        try {
            AssetValidation.validate(name,bytes);
            throw new AssertionError("accepted "+name);
        }
        catch(IOException expected) {
        }
    }
    public static int verify()throws Exception {
        checks=0;
        Map<String,PermissionRules.Action> rules=new LinkedHashMap<>();
        rules.put("read",new PermissionRules.Action("read",true,false,List.of()));
        rules.put("update",new PermissionRules.Action("update",true,false,List.of("read")));
        rules.put("future",new PermissionRules.Action("future",false,false,List.of()));
        rules.put("admin",new PermissionRules.Action("admin",true,true,List.of()));
        var selected=grants();
        selected.get("GUEST").add("read");
        PermissionRules.validate(rules,selected);
        check(selected.get("TIER1").isEmpty(),"tiers must not inherit");
        check(!selected.get("GUEST").contains("future"),"new codes deny by default");
        selected.get("TIER1").add("update");
        reject(()->PermissionRules.validate(rules,selected),"missing dependency");
        selected.get("TIER1").add("read");
        PermissionRules.validate(rules,selected);
        check(PermissionRules.closure("update",rules).equals(Set.of("update","read")),"dependency closure");
        for(String code:List.of("future","admin","unknown")) {
            var x=grants();
            x.get("TIER3").add(code);
            reject(()->PermissionRules.validate(rules,x),"blocked code "+code);
        }
        check(PermissionRules.permitted("update",rules,Set.of("read","update"),false),"complete grants accepted");
        check(!PermissionRules.permitted("update",rules,Set.of("update"),false),"dependency denied");
        var unavailableDep=new LinkedHashMap<>(rules);unavailableDep.put("read",new PermissionRules.Action("read",false,false,List.of()));
        check(!PermissionRules.permitted("update",unavailableDep,Set.of("read","update"),false),"inactive dependency denied");
        check(!PermissionRules.permitted("future",rules,Set.of(),true),"admin cannot activate future");
        check(PermissionRules.permitted("update",rules,Set.of(),true),"admin own test uses implemented active actions");
        var duplicate=grants();
        duplicate.get("GUEST").addAll(List.of("read","read"));
        reject(()->PermissionRules.validate(rules,duplicate),"duplicate grant");
        var partial=grants();
        partial.remove("TIER3");
        reject(()->PermissionRules.validate(rules,partial),"all four tiers");
        var cyclic=new LinkedHashMap<>(rules);
        cyclic.put("read",new PermissionRules.Action("read",true,false,List.of("update")));
        reject(()->PermissionRules.validate(cyclic,grants()),"dependency cycle");
        var missing=new LinkedHashMap<>(rules);
        missing.put("read",new PermissionRules.Action("read",true,false,List.of("absent")));
        reject(()->PermissionRules.cycle(missing),"missing dependency definition");
        check(Inputs.email(" User@Example.com ").equals("user@example.com"),"canonical email");
        check(Inputs.phone("010-1234-5678").equals("+821012345678"),"KR local phone");
        check(Inputs.phone("+82 10 1234 5678").equals("+821012345678"),"KR international phone");
        check(Inputs.phone("821012345678").equals("+821012345678"),"KR prefix");
        for(String invalid:List.of("bad","a..b@sample.com","x@example..com","@example.com","x@localhost","x@例え.jp"))reject(()->Inputs.email(invalid),"email rejection");
        for(String invalid:List.of("+12025550123","01112345678","010123","010123456789","010<script>"))reject(()->Inputs.phone(invalid),"phone rejection");
        check(Inputs.code("workspace.draft.read").equals("workspace.draft.read"),"action code");
        for(String value:List.of("../config","UPPER","a","<script>","a/b/c"))reject(()->Inputs.code(value),"code rejection");
        reject(()->Inputs.text("x\0",100,"value"),"NUL rejected");
        reject(()->Inputs.text("    ",100,"value"),"empty rejected");
        check(Crypto.sha256("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"SHA256 known vector");
        check(Crypto.hmac("key","The quick brown fox jumps over the lazy dog").equals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"),"HMAC known vector");
        check(Crypto.equal("abc","abc")&&!Crypto.equal("abc","abd"),"MAC compare");
        Set<String> tokens=new HashSet<>();
        for(int i=0;
        i<50;
        i++) {
            String token=Crypto.token();
            check(token.matches("[A-Za-z0-9_-]{43}"),"reset token length and encoding");
            check(tokens.add(token),"random reset tokens");
            check(Crypto.otp().matches("[0-9]{6}"),"six-digit OTP");
        }
        byte[] text="한글 메모\nUTF-8".getBytes(StandardCharsets.UTF_8);
        var txt=AssetValidation.validate("note.txt",text);
        check(Arrays.equals(txt.data(),text)&&txt.mediaType().equals("text/plain"),"UTF8 original preserved");
        badFile("bad.txt",new byte[] {
            (byte)0xc3,0x28
        }
        );
        badFile("bad.txt",new byte[] {
            65,0,66
        }
        );
        badFile("empty.txt",new byte[0]);
        badFile("huge.txt",new byte[10*1024*1024+1]);
        badFile("archive.zip",new byte[] {
            80,75,3,4
        }
        );
        badFile("fake.png",text);
        BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ImageIO.write(image,"png",out);
        byte[] png=out.toByteArray();
        check(Arrays.equals(AssetValidation.validate("photo.png",png).data(),png),"image bytes preserved");
        badFile("mismatch.jpg",png);
        image=new BufferedImage(8001,1,BufferedImage.TYPE_INT_RGB);
        out.reset();
        ImageIO.write(image,"png",out);
        badFile("wide.png",out.toByteArray());
        return checks;
    }
    public static void main(String[] args)throws Exception {
        System.out.println("P1_CORE production-class checks="+verify()+" passed");
    }
}
