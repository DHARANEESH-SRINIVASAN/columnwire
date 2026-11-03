package io.github.dharaneeshsrinivasan.columnwire.spring;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class VaryOnAcceptAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ArrowHttpMessageConverter.writable(bodyType(returnType), null);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        if (!headers.getVary().contains(HttpHeaders.ACCEPT)) {
            headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        }
        return body;
    }

    private static ResolvableType bodyType(MethodParameter returnType) {
        ResolvableType type = ResolvableType.forMethodParameter(returnType);
        return HttpEntity.class.isAssignableFrom(returnType.getParameterType()) ? type.getGeneric(0) : type;
    }
}
