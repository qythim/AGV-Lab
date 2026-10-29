package com.agvlab.rcs.map.controller;

import java.util.NoSuchElementException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = MapController.class)
public class MapExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ProblemDetail invalid(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                e instanceof HttpMessageNotReadableException ? "地圖 JSON 格式不正確" : e.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail missing(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
