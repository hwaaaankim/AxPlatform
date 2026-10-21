package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.*;

/** Bounded arithmetic only: [stableColumnId], decimal literals, + - * / and parentheses. No eval. */
public final class SheetFormula {
    private static final Pattern REF=Pattern.compile("\\[([A-Za-z][A-Za-z0-9_-]{0,39})]");
    private SheetFormula() { }
    public static Set<String> references(String expression) {
        Set<String> ids=new LinkedHashSet<>();var m=REF.matcher(expression);while(m.find())ids.add(m.group(1));return ids;
    }
    public static BigDecimal evaluate(String expression, Function<String,String> resolver) {
        if(expression==null||expression.length()>500)throw Problem.invalid("계산식은 500자 이내입니다.");
        return new Parser(expression,resolver).parse();
    }
    public static void calculate(List<Map<String,Object>> columns,List<List<String>> rows) {
        Map<String,Integer> indexes=new LinkedHashMap<>();for(int i=0;i<columns.size();i++)indexes.put((String)columns.get(i).get("id"),i);
        List<Integer> order=new ArrayList<>();Set<Integer> visited=new HashSet<>(),visiting=new HashSet<>();
        for(int i=0;i<columns.size();i++)visit(i,columns,indexes,visited,visiting,order);
        for(int i:order){String formula=(String)columns.get(i).get("formula");if(formula==null)continue;
            for(int r=0;r<rows.size();r++){
                final List<String> row=rows.get(r);
                try {BigDecimal value=evaluate(formula,id->row.get(indexes.get(id)));
                    if(value==null)row.set(i,"");else {value=value.setScale(12,RoundingMode.HALF_UP).stripTrailingZeros();
                        if(value.precision()>15)throw Problem.invalid("계산 결과가 15자리 정밀도를 초과합니다.");row.set(i,value.toPlainString());}
                }catch(Problem ex){throw Problem.invalid((r+1)+"행 · "+columns.get(i).get("label")+": "+ex.getMessage());}
            }
        }
    }
    private static void visit(int i,List<Map<String,Object>> cols,Map<String,Integer> indexes,Set<Integer> visited,Set<Integer> visiting,List<Integer> order){
        if(visited.contains(i))return;if(!visiting.add(i))throw Problem.invalid("계산식에 순환 참조가 있습니다.");
        Object f=cols.get(i).get("formula");if(f instanceof String formula){
            if(!"NUMBER".equals(cols.get(i).get("type")))throw Problem.invalid("계산 열은 숫자 형식이어야 합니다.");
            for(String id:references(formula)){Integer at=indexes.get(id);if(at==null)throw Problem.invalid("계산식에서 참조한 열이 없습니다: "+id);
                if(!"NUMBER".equals(cols.get(at).get("type")))throw Problem.invalid("계산식은 숫자 열만 참조할 수 있습니다: "+cols.get(at).get("label"));visit(at,cols,indexes,visited,visiting,order);}
            evaluate(formula,id->"");
        }
        visiting.remove(i);visited.add(i);order.add(i);
    }
    public static String excel(String expression,Map<String,Integer> index,int row){
        var m=REF.matcher(expression);StringBuffer out=new StringBuffer();while(m.find()){Integer at=index.get(m.group(1));if(at==null)throw Problem.invalid("계산 참조가 없습니다.");m.appendReplacement(out,Matcher.quoteReplacement(""+(char)('A'+at)+row));}m.appendTail(out);return out.toString();
    }
    private static final class Parser {
        final String s;final Function<String,String> values;int p,depth;boolean missing;
        Parser(String s,Function<String,String> values){this.s=s;this.values=values;}
        BigDecimal parse(){BigDecimal result=sum();space();if(p!=s.length())throw bad();return missing?null:result;}
        void space(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;}
        boolean take(char c){space();if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}
        BigDecimal sum(){BigDecimal n=product();while(true){if(take('+'))n=n.add(product(),MathContext.DECIMAL128);else if(take('-'))n=n.subtract(product(),MathContext.DECIMAL128);else return n;}}
        BigDecimal product(){BigDecimal n=atom();while(true){if(take('*'))n=n.multiply(atom(),MathContext.DECIMAL128);else if(take('/')){BigDecimal d=atom();if(d.signum()==0&&!missing)throw Problem.invalid("0으로 나눌 수 없습니다.");n=missing?BigDecimal.ZERO:n.divide(d,12,RoundingMode.HALF_UP);}else return n;}}
        BigDecimal atom(){if(++depth>32)throw bad();try{if(take('+'))return atom();if(take('-'))return atom().negate();if(take('(')){var n=sum();if(!take(')'))throw bad();return n;}space();
            if(take('[')){int begin=p;while(p<s.length()&&s.charAt(p)!=']')p++;if(p==s.length())throw bad();String id=s.substring(begin,p++);if(!id.matches("[A-Za-z][A-Za-z0-9_-]{0,39}"))throw bad();String value=values.apply(id);if(value==null||value.isBlank()){missing=true;return BigDecimal.ZERO;}try{return new BigDecimal(value);}catch(NumberFormatException ex){throw bad();}}
            int begin=p;while(p<s.length()&&(Character.isDigit(s.charAt(p))||s.charAt(p)=='.'))p++;if(begin==p)throw bad();String number=s.substring(begin,p);if(!number.matches("[0-9]{1,15}(\\.[0-9]{1,12})?"))throw bad();return new BigDecimal(number);
        }finally{depth--;}}
        Problem bad(){return Problem.invalid("계산식은 [열ID], 숫자, + - * /, 괄호만 사용할 수 있습니다. 위치 "+(p+1));}
    }
}
