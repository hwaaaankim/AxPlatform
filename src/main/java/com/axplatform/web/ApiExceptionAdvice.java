package com.axplatform.web;

import java.io.IOException;
import java.util.*;
import com.axplatform.common.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.http.converter.HttpMessageNotReadableException;
@ControllerAdvice public class ApiExceptionAdvice {
    private final JsonCodec json;
    public ApiExceptionAdvice(JsonCodec json) {
        this.json=json;
    }
    @ExceptionHandler(Problem.class) public ModelAndView problem(Problem p,HttpServletRequest r,HttpServletResponse response)throws IOException {
        if(ApiErrorWriter.isAsync(r)) {
            response.setStatus(p.status());
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setHeader("Cache-Control","no-store");
            response.getWriter().write(json.write(Map.of("code",p.code(),"message",p.getMessage(),"details",p.details())));
            return null;
        }
        return respond(r,response,p.status(),p.code(),p.getMessage());
    }
    @ExceptionHandler( {
        MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class
    }
    ) public ModelAndView invalid(HttpServletRequest r,HttpServletResponse response)throws IOException {
        return respond(r,response,400,"INVALID_REQUEST","요청값의 형식을 확인해 주세요.");
    }
    @ExceptionHandler(DataAccessException.class) public ModelAndView database(HttpServletRequest r,HttpServletResponse response)throws IOException {
        return respond(r,response,503,"DATABASE_UNAVAILABLE","자료를 읽거나 저장하지 못했습니다. 추가 SQL 적용 여부를 확인해 주세요.");
    }
    private ModelAndView respond(HttpServletRequest r,HttpServletResponse response,int status,String code,String message)throws IOException {
        if(ApiErrorWriter.isAsync(r)) {
            ApiErrorWriter.write(response,status,code,message);
            return null;
        }
        response.setStatus(status);
        var view=new ModelAndView("error/failure");
        view.addObject("message",message);
        return view;
    }
}
