package com.axplatform.ai;

import com.axplatform.common.Problem;
import com.axplatform.sheet.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class AiContinuationTest {
    private Map<String,Object> table(int width,int count,String text){
        var columns=IntStream.range(0,width).mapToObj(i->Map.<String,Object>of("id","c"+i,"label","열"+i,"type","TEXT","unit","","width",160)).toList();
        return Map.of("columns",columns,"rows",Collections.nCopies(count,Collections.nCopies(width,text)),"rowIds",IntStream.range(0,count).mapToObj(i->"r"+i).toList());
    }
    private Map<String,Object> book(Map<String,Object> data){return Map.of("format","AX_WORKBOOK_1","activeSheetId","sheet1","sheets",List.of(Map.of("id","sheet1","name","자료","sourceAssetIds",List.of(),"data",data)));}
    private Map<String,Object> proposal(Map<String,Object> sheet,int next,boolean more){
        var p=new LinkedHashMap<String,Object>();p.put("sheet",sheet);p.put("operations",List.of());p.put("continuation",Map.of("hasMore",more,"nextRow",next,"note","원본 행 확인"));return p;
    }
    private Map<String,Object> appended(int count,int next,boolean more){
        var p=proposal(null,next,more);p.put("operations",List.of(Map.of("kind","APPEND_ROWS","rows",Collections.nCopies(count,List.of("새 값")))));return p;
    }
    private Map<String,Object> photo(Map<String,Object> book,int chunk){
        return AiContinuation.initial(Map.of("mode","PHOTO","chunkRows",chunk,"sourceAssetIds",List.of("10000000-0000-0000-0000-000000000001","10000000-0000-0000-0000-000000000002")),book,"sheet1","root");
    }

    @Test void actualWidthSetsCellBudgetAndUnknownWidthCannotApplyAnOversizedFirstChunk(){
        var wide=book(table(100,0,""));
        assertThat(AiContinuation.initial(Map.of("mode","GENERATE","chunkRows",200),wide,"sheet1","root")).containsEntry("chunkRows",40);
        var empty=book(table(0,0,""));var plan=AiContinuation.initial(Map.of("mode","GENERATE","chunkRows",200),empty,"sheet1","root");
        assertThatThrownBy(()->AiContinuation.result(plan,proposal(table(100,200,"v"),201,true),empty)).isInstanceOf(Problem.class).extracting("code").isEqualTo("AI_OUTPUT_LIMIT");
    }

    @Test void targetBeyond200AdvancesOnlyAfterApplyAndEndsAtExactRequestedCount(){
        var book=book(table(1,1,"existing"));var plan=AiContinuation.initial(Map.of("mode","GENERATE","targetRows",450,"chunkRows",200),book,"sheet1","root");
        for(int count:List.of(200,200,50)){
            int before=AiContinuation.number(plan,"processedRows"),cursor=AiContinuation.number(plan,"sourceRowStart");
            var checked=AiContinuation.result(plan,appended(count,cursor+count,false),book);var result=Map.<String,Object>of("continuationResult",checked);
            assertThat(AiContinuation.publicState(plan,"READY",result,"")).containsEntry("processedRows",before).containsEntry("nextAllowed",false);
            var applied=AiContinuation.publicState(plan,"APPLIED",result,"");assertThat(applied).containsEntry("processedRows",before+count);
            if(before+count<450)plan=AiContinuation.prepare(AiContinuation.resume(Map.of("continuation",plan),"APPLIED",result,""),book);
            else assertThat(applied).containsEntry("done",true).containsEntry("status","COMPLETED").containsEntry("nextAllowed",false);
        }
    }

    @Test void aFullPhotoChunkCannotDeclareEofUntilNextBoundaryIsChecked(){
        var book=book(table(1,1,"old"));var plan=photo(book,200);
        var result=AiContinuation.result(plan,appended(200,201,false),book);
        var next=AiContinuation.object(result.get("next"));assertThat(next).containsEntry("sourceIndex",0).containsEntry("endCheckNeeded",true).containsEntry("sourceRowStart",201).containsEntry("done",false);
        var eof=AiContinuation.result(next,proposal(null,201,false),book);assertThat(eof).containsEntry("producedRows",0).containsEntry("requiredField","continuation");
        assertThat(AiContinuation.object(eof.get("next"))).containsEntry("sourceIndex",1).containsEntry("sourceRowStart",1).containsEntry("processedRows",200);
    }

    @Test void unknownGenerationTargetAlsoChecksFullBoundaryBeforeZeroRowCompletion(){
        var book=book(table(1,1,"old"));var plan=AiContinuation.initial(Map.of("mode","GENERATE","chunkRows",10),book,"sheet1","root");
        var next=AiContinuation.object(AiContinuation.result(plan,appended(10,11,false),book).get("next"));
        assertThat(next).containsEntry("done",false).containsEntry("endCheckNeeded",true);
        assertThat(AiContinuation.result(next,proposal(null,11,false),book)).containsEntry("done",true).containsEntry("requiredField","continuation");
    }

    @Test void blankTemplateNeedsItsColumnsAppliedAndCannotFulfillAnExplicitRowTarget(){
        var book=book(table(0,0,""));var initial=AiContinuation.initial(Map.of("mode","GENERATE"),book,"sheet1","root");
        var template=proposal(table(5,0,""),1,false);
        assertThat(AiContinuation.result(initial,template,book)).containsEntry("done",true).containsEntry("requiredField","sheet");
        initial.put("targetRows",500);
        assertThatThrownBy(()->AiContinuation.result(initial,template,book)).isInstanceOf(Problem.class);
    }

    @Test void rejectsFalseCursorAndZeroProgressLoopWithoutAdvancingSource(){
        var book=book(table(1,1,"old"));var plan=photo(book,100);
        assertThatThrownBy(()->AiContinuation.result(plan,appended(10,20,true),book)).isInstanceOf(Problem.class).hasMessageContaining("실제 추가 행 수");
        assertThatThrownBy(()->AiContinuation.result(plan,proposal(null,1,true),book)).isInstanceOf(Problem.class).hasMessageContaining("새 행 없이");
        assertThat(plan).containsEntry("sourceRowStart",1).containsEntry("processedRows",0);
    }

    @Test void rangeShrinksByActualTextAndRejectsRowOrderChanges(){
        var data=table(1,100,"x".repeat(2000));var book=book(data);
        var plan=AiContinuation.initial(Map.of("mode","RANGE","rowStart",1,"rowCount",100,"chunkRows",100),book,"sheet1","root");
        assertThat(plan).containsEntry("chunkRows",25);
        var reordered=new LinkedHashMap<>(data);var ids=new ArrayList<>((List<String>)data.get("rowIds"));Collections.swap(ids,0,1);reordered.put("rowIds",ids);
        assertThatThrownBy(()->AiContinuation.prepare(plan,book(reordered))).isInstanceOf(Problem.class).extracting("code").isEqualTo("AI_SOURCE_CHANGED");
    }

    @Test void retryKeepsExactCursorAndOnlyKnownLimitsReduceTheNextRequest(){
        var book=book(table(1,1,"old"));var plan=photo(book,200);plan.put("sourceRowStart",201);plan.put("processedRows",200);
        assertThat(AiContinuation.resume(Map.of("continuation",plan),"FAILED",Map.of(),"AI_OUTPUT_LIMIT")).containsEntry("chunkRows",100).containsEntry("sourceRowStart",201).containsEntry("processedRows",200);
        assertThat(AiContinuation.resume(Map.of("continuation",plan),"FAILED",Map.of(),"AI_PROVIDER_POLICY")).containsEntry("chunkRows",200);
        assertThat(AiContinuation.resume(Map.of("continuation",plan),"FAILED",Map.of(),"AI_SOURCE_CHANGED")).containsEntry("sourceRowStart",201);
        plan.put("chunkRows",1);assertThat(AiContinuation.publicState(plan,"FAILED",Map.of(),"AI_OUTPUT_LIMIT")).containsEntry("status","NEEDS_INPUT").containsEntry("needsInstruction",true).containsEntry("nextAllowed",false).containsEntry("canRetry",false);
    }

    @Test void questionsPreserveCheckpointAndAbandonClosesChainWithoutClaimingCompletion(){
        var book=book(table(1,1,"old"));var plan=photo(book,100);plan.put("sourceRowStart",201);plan.put("processedRows",200);
        var questions=Map.<String,Object>of("proposal",Map.of("ready",false));
        assertThat(AiContinuation.resume(Map.of("continuation",plan),"READY",questions,"")).containsEntry("sourceRowStart",201).containsEntry("processedRows",200);
        assertThat(AiContinuation.publicState(plan,"READY",questions,"")).containsEntry("status","NEEDS_INPUT");
        plan.put("abandoned",true);
        assertThat(AiContinuation.publicState(plan,"CANCELLED",Map.of(),"")).containsEntry("status","ABANDONED").containsEntry("done",false).containsEntry("nextAllowed",false);
        assertThatThrownBy(()->AiContinuation.resume(Map.of("continuation",plan),"CANCELLED",Map.of(),"")).isInstanceOf(Problem.class);
    }

    @Test void knownStorageHeadroomCapsChunkBeforeProviderCall(){
        var nearFull=book(table(1,49999,""));
        assertThat(AiContinuation.initial(Map.of("mode","GENERATE","chunkRows",200),nearFull,"sheet1","root")).containsEntry("chunkRows",1);
        assertThatThrownBy(()->AiContinuation.initial(Map.of("mode","GENERATE"),book(table(1,50000,"")),"sheet1","root")).isInstanceOf(Problem.class).extracting("code").isEqualTo("AI_DESTINATION_FULL");
    }

    @Test void knownWorkbookFormulaQuotaCapsRowsBeforeProviderCall(){
        var other=new LinkedHashMap<>(table(4,49999,""));var calculated=new ArrayList<Map<String,Object>>();for(Object c:(List<?>)other.get("columns")){var column=new LinkedHashMap<>((Map<String,Object>)c);column.put("formula","1");calculated.add(column);}other.put("columns",calculated);
        var target=new LinkedHashMap<>(table(1,1,""));var col=new LinkedHashMap<>((Map<String,Object>)((List<?>)target.get("columns")).get(0));col.put("formula","1");target.put("columns",List.of(col));
        var book=Map.<String,Object>of("format","AX_WORKBOOK_1","activeSheetId","sheet1","sheets",List.of(Map.of("id","sheet1","name","대상","sourceAssetIds",List.of(),"data",target),Map.of("id","sheet2","name","기존","sourceAssetIds",List.of(),"data",other)));
        assertThat(AiContinuation.initial(Map.of("mode","GENERATE","chunkRows",200),book,"sheet1","root")).containsEntry("chunkRows",3);
    }

    @Test void proposalTableUsesSame80000CharacterBoundAsAppendOperations(){
        var p=proposal(table(1,41,"x".repeat(2000)),42,false);p.put("summary","검토");p.put("confirmed",List.of());p.put("assumptions",List.of());p.put("questions",List.of());p.put("draft",Map.of("title","자료","answers",Map.of("purpose","","data","","result","","details","","audience","")));p.put("warnings",List.of());p.put("ready",true);
        assertThatThrownBy(()->ProposalRules.normalize(p,"EXCEL")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void longInstructionsRequireExplicitReplacementAndNeverLoseTheCursor(){
        var plan=new LinkedHashMap<String,Object>(Map.of("originalPrompt","지".repeat(12000),"sourceRowStart",201,"processedRows",200));
        assertThatThrownBy(()->AiContinuation.instructions(plan,"추가 답변",false)).isInstanceOf(Problem.class).extracting("code").isEqualTo("AI_INSTRUCTION_LIMIT");
        assertThat(AiContinuation.instructions(plan,"새 전체 지침과 답변",true)).isEqualTo("새 전체 지침과 답변");
        assertThat(plan).containsEntry("sourceRowStart",201).containsEntry("processedRows",200).containsEntry("originalPrompt","지".repeat(12000));
    }

    @Test void fixedSourceFailureCannotOfferTheSamePaidRetry(){
        var book=book(table(1,1,"old"));var plan=photo(book,100);
        assertThat(AiContinuation.publicState(plan,"FAILED",Map.of(),"AI_IMAGE_TOO_LARGE")).containsEntry("status","SOURCE_REQUIRED").containsEntry("needsSourceReplacement",true).containsEntry("nextAllowed",false);
        assertThatThrownBy(()->AiContinuation.resume(Map.of("continuation",plan),"FAILED",Map.of(),"AI_IMAGE_TOO_LARGE")).isInstanceOf(Problem.class).extracting("code").isEqualTo("AI_SOURCE_REPLACEMENT_REQUIRED");
        assertThatThrownBy(()->AiContinuation.initial(Map.of("mode","RANGE"),book(table(100,1,"x".repeat(2000))),"sheet1","root")).isInstanceOf(Problem.class).extracting("code").isEqualTo("AI_SINGLE_ROW_LIMIT");
    }
}
