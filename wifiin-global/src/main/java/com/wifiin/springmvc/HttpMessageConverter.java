package com.wifiin.springmvc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wifiin.common.CommonConstant;
import com.wifiin.common.GlobalObject;
import com.wifiin.data.exception.DataTransformerException;
import com.wifiin.exception.CompressorException;
import com.wifiin.spring.ApplicationContextHolder;
import com.wifiin.springmvc.exception.HttpMessageConverterInitException;
import com.wifiin.util.Help;
import com.wifiin.util.io.IOUtil;

/**
 * springmvc请求/响应转化
 * @author Running
 *
 * @param <E>
 */

public class HttpMessageConverter<E> extends AbstractGenericHttpMessageConverter<E>{
    private static final Logger log=LoggerFactory.getLogger(HttpMessageConverter.class);
    private static final String[] ONE_EMPTY_STRING_ARRAY={""};
    private List<MediaType> requestMediaTypes;
    private List<MediaType> responseMediaTypes;
    private CryptoType defaultCryptoType=CryptoType.NONE;
    private Map<String,Cryptor> cryptors;
    private Map<String,Compressor> requestCompressor;
    private Map<String,Compressor> responseCompressor;
    private Map<CompressorType,Compressor> compressors;
    private Charset charset;
    
    public void HttpMessageConverter(){
        Pattern moreThanOnePathSplitor=Pattern.compile("/{2,}");
        Pattern endingPathSplitor=Pattern.compile("/$");
        Map<CryptoType,Cryptor> cryptors=cryptors();
        Map<CompressorType,Compressor> compressors=compressors();
        Map<String,Cryptor> finalCryptors=Maps.newHashMap();
        Map<String,Compressor> requestCompressor=Maps.newHashMap();
        Map<String,Compressor> responseCompressor=Maps.newHashMap();
        ApplicationContextHolder.getInstance().getBeansWithAnnotation(Controller.class).values().forEach((controller)->{
            Class cls=controller.getClass();
            String[] requestMappingsOnController=getRequestMappings(cls);
            Method[] methods=cls.getMethods();
            Arrays.stream(requestMappingsOnController).forEach((rmc)->{
                Arrays.stream(methods).forEach((m)->{
                    String[] requestMappingsOnMethod=getRequestMappings(m);
                    CryptoType crypto=getHttpMessageCryptoType(m);
                    CompressorType requestCompressorType=getHttpRequestMessageCompressorType(m);
                    CompressorType responseCompressorType=getHttpResponseMessageCompressorType(m);
                    Arrays.stream(requestMappingsOnMethod).forEach((rmm)->{
                        String path=Help.concat(new String[]{rmc,rmm},"/");
                        path=moreThanOnePathSplitor.matcher(path).replaceAll("");
                        path=endingPathSplitor.matcher(path).replaceAll("");
                        finalCryptors.put(path,cryptors.get(crypto));
                        requestCompressor.put(path,compressors.get(requestCompressorType));
                        responseCompressor.put(path,compressors.get(responseCompressorType));
                    });
                });
            });
        });
        this.cryptors=finalCryptors;
        this.requestCompressor=requestCompressor;
        this.responseCompressor=responseCompressor;
        this.compressors=compressors;
    }
    private Map<CryptoType,Cryptor> cryptors(){
        Map<String,Cryptor> cryptorBeans=ApplicationContextHolder.getInstance().getBeans(Cryptor.class);
        Map<CryptoType,Cryptor> cryptors=Maps.newHashMap(); 
        if(Help.isNotEmpty(cryptorBeans)){
            cryptorBeans.values().forEach((c)->{
                Cryptor prev=cryptors.put(c.crypto(),c);
                if(prev!=null){
                    throw new HttpMessageConverterInitException("cryptor type is duplicated:"+c.crypto()+"; one is "+prev+"; another is "+c);
                }
            });
        }
        return cryptors;
    }
    private Map<CompressorType,Compressor> compressors(){
        Map<String,Compressor> compressorBeans=ApplicationContextHolder.getInstance().getBeans(Compressor.class);
        Map<CompressorType,Compressor> compressors=Maps.newHashMap(); 
        if(Help.isNotEmpty(compressorBeans)){
            compressorBeans.values().forEach((c)->{
                Compressor prev=compressors.put(c.compressorType(),c);
                if(prev!=null){
                    throw new HttpMessageConverterInitException("compressor type is duplicated:"+c.compressorType()+"; one is "+prev+"; another is "+c);
                }
            });
        }
        return compressors;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private String[] getRequestMappings(Class controller){
        RequestMapping requestMapping=(RequestMapping)controller.getAnnotation(RequestMapping.class);
        return getRequestMappings(requestMapping);
    }
    private String[] getRequestMappings(Method method){
        RequestMapping requestMapping=(RequestMapping)method.getAnnotation(RequestMapping.class);
        return getRequestMappings(requestMapping);
    }
    private String[] getRequestMappings(RequestMapping requestMapping){
        if(requestMapping==null){
            return ONE_EMPTY_STRING_ARRAY;
        }
        return requestMapping.value();
    }
    private CryptoType getHttpMessageCryptoType(Method method){
        CryptedMessage message=(CryptedMessage)method.getAnnotation(CryptedMessage.class);
        if(message==null){
            return this.defaultCryptoType;
        }else{
            return message.value();
        }
    }
    private CompressorType getHttpRequestMessageCompressorType(Method method){
        CompressedRequest message=(CompressedRequest)method.getAnnotation(CompressedRequest.class);
        if(message==null){
            return CompressorType.NONE;
        }
        return message.value();
    }
    private CompressorType getHttpResponseMessageCompressorType(Method method){
        CompressedResponse message=(CompressedResponse)method.getAnnotation(CompressedResponse.class);
        if(message==null){
            return CompressorType.NONE;
        }
        return message.value();
    }
    public String getRequestMediaType(){
        return requestMediaTypes.toString();
    }

    public void setRequestMediaType(String mediaType){
        if(requestMediaTypes==null){
            requestMediaTypes=Lists.newArrayList();
        }
        int idx=mediaType.indexOf(';');
        if(idx<0){
            this.requestMediaTypes.add(MediaType.parseMediaType(mediaType));
            charset=Charset.forName(CommonConstant.DEFAULT_CHARSET_NAME);
        }else{
            this.requestMediaTypes.add(MediaType.parseMediaType(mediaType.substring(0, idx).trim()));
            charset=Charset.forName(mediaType.substring(idx+1).trim());
        }
    }
    @SuppressWarnings("unchecked")
    public String getResponseMediaType(){
        return Help.convert(responseMediaTypes,requestMediaTypes).toString();
    }
    public void setResponseMediaType(String responseMediaType){
        if(responseMediaTypes==null){
            responseMediaTypes=Lists.newArrayList();
        }
        int idx=responseMediaType.indexOf(';');
        if(idx<0){
            this.responseMediaTypes.add(MediaType.parseMediaType(responseMediaType));
        }else{
            this.responseMediaTypes.add(MediaType.parseMediaType(responseMediaType.substring(0, idx).trim()));
        }
    }
    public List<String> getRequestMediaTypes(){
        return getMediaTypes(requestMediaTypes);
    }
    public void setRequestMediaTypes(List<String> mediaTypes){
        setMediaTypes(mediaTypes,(m)->{
            setRequestMediaType(m);
        });
    }
    public List<String> getResponseMediaTypes(){
        return getMediaTypes(responseMediaTypes);
    }
    public void setResponseMediaTypes(List<String> mediaTypes){
        setMediaTypes(mediaTypes,(m)->{
            setResponseMediaType(m);
        });
    }
    private List<String> getMediaTypes(List<MediaType> mediaTypes){
        List<String> mediaTypeNames=Lists.newArrayList();
        for(int i=0,l=mediaTypes.size();i<l;i++){
            mediaTypeNames.add(mediaTypes.get(i).toString());
        }
        return mediaTypeNames;
    }
    private void setMediaTypes(List<String> mediaTypes,Consumer<String> consumer){
        for(int i=0,l=mediaTypes.size();i<l;i++){
            consumer.accept(mediaTypes.get(i));
        }
    }
    public CryptoType getDefaultCryptoType(){
        return defaultCryptoType;
    }
    public void setDefaultCryptoType(CryptoType defaultCryptoType){
        this.defaultCryptoType=defaultCryptoType;
    }
    private HttpServletRequest request(){
        return SpringMVCContext.getRequest();
    }
    /**
     * 读取请求报文字节数组
     * @param inMsg
     * @return 请求体内容
     * @throws IOException
     */
    protected byte[] read(HttpInputMessage inMsg) throws Exception{
        return IOUtil.read(inMsg.getBody(),(int)inMsg.getHeaders().getContentLength());
    }
    /**
     * 转换报文格式，可以不覆盖。比如把base64编码的字节数组转换成原始报文。本方法已经实现将base64字节转成原始字节数组，如果需要使用其它转换方式应覆盖此方法
     * @param content
     * @return 
     */
    protected byte[] inputMessageConvert(byte[] content) throws Exception{
        return Base64.decodeBase64(content);
    }
    /**
     * 解密，如果报文没有加密就不用覆盖此方法
     * @param buf 密文
     * @return 明文
     */
    protected byte[] decrypt(byte[] buf) throws Exception{
        String uri=request().getRequestURI();
        int idx=uri.indexOf("/v")+2;
        String v=uri.substring(idx,uri.indexOf("/",idx));
        return this.cryptors.get(uri).encrypt(v,buf);
    }
    /**
     * 解压，如果报文没有压缩就不用覆盖此方法
     * @param buf 压缩的报文
     * @return 解压的报文
     */
    protected byte[] uncompress(byte[] buf) throws Exception{
        HttpServletRequest request=request();
        String compressed=request.getQueryString();
        try{
            if(Help.isEmpty(compressed)){
                return this.requestCompressor.get(request.getRequestURI()).uncompress(buf);
            }else{
                return this.compressors.get(CompressorType.valueOf(compressed.toUpperCase())).compress(buf);
            }
        }catch(CompressorException e){
            return buf;
        }
    }
    /**
     * 把报文的字节数组转换成对象，当前实现是把字节数组buf当作json解析成ctxCls的对象
     * @param buf 报文字节数组
     * @return 转换后的对象
     * @throws InvocationTargetException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
     */
    @SuppressWarnings({"unchecked"})
    protected E convert(Type type,Class<?> ctxCls,byte[] buf) throws Exception{
        return GlobalObject.getJsonMapper().readValue(buf, (Class<E>)type);
    }
    @Override
    public E read(Type type,Class<?> ctxCls, HttpInputMessage inMsg) throws IOException,HttpMessageNotReadableException{
        byte[] buf=null;
        try{
            buf=read(inMsg);
            buf=inputMessageConvert(buf);
            buf=decrypt(buf);
            buf=uncompress(buf);
            E e=convert(type,ctxCls,buf);
            request().setAttribute(SpringMVCConstant.REQUEST_PARAMS,e);
            return e;
        }catch(Exception e){
            log.warn("HttpMessageConverter.read:{};{}",Base64.encodeBase64String(buf),e);
            throw new DataTransformerException(e);
        }
    }

    @Override
    protected void writeInternal(E t,Type type, HttpOutputMessage outMsg) throws IOException,HttpMessageNotWritableException{
        byte[] content=null;
        try{
            HttpServletRequest request=request();
            if(Help.isEmpty(request.getAttribute(SpringMVCConstant.RESULT_FOR_LOG))){
                request.setAttribute(SpringMVCConstant.RESULT_FOR_LOG,t);
            }
            content=convert(t,type);
            content=compress(content);
            content=encrypt(content);
            content=outputMessageConvert(content);
            outMsg.getBody().write(content);
        }catch(Exception e){
            log.warn("HttpMessageConverter.write:{};{}",Base64.encodeBase64String(content),e);
            throw new DataTransformerException(e);
        }
    }
    /**
     * 把响应数据转化为字节数组，默认是转为JSON的字节数组。
     * @param t 响应数据对象
     * @param type 响应对象类型
     * @return 响应数据字节
     * @throws Exception
     */
    protected byte[] convert(E t,Type type)throws Exception{
        return GlobalObject.getJsonMapper().writeValueAsString(t).getBytes(charset);
    }
    /**
     * 压缩响应数据，如果不需要压缩可以不覆盖此方法
     * @param content
     * @return
     */
    protected byte[] compress(byte[] content)throws Exception{
        HttpServletRequest request=request();
        String compressed=request.getQueryString();
        try{
            if(Help.isEmpty(compressed)){
                return this.responseCompressor.get(request.getRequestURI()).compress(content);
            }else{
                return this.compressors.get(CompressorType.valueOf(compressed.toUpperCase())).compress(content);
            }
        }catch(CompressorException e){
            return content;
        }
    }
    
    /**
     * 加密响应数据，如果不需要加密可以不覆盖此方法
     * @param content
     * @return
     */
    protected byte[] encrypt(byte[] content)throws Exception{
        String uri=request().getRequestURI();
        int idx=uri.indexOf("/v")+2;
        String v=uri.substring(idx,uri.indexOf("/",idx));
        return this.cryptors.get(uri).encrypt(v,content);
    }
    /**
     * 把响应数据转化成要写回客户端，默认什么也不干。比如转为base64再转化成字节数组
     * @param content
     * @return
     */
    protected byte[] outputMessageConvert(byte[] content)throws Exception{
        return Base64.encodeBase64(content);
    }
    @Override
    protected E readInternal(Class<? extends E> clazz, HttpInputMessage inMsg) throws IOException,HttpMessageNotReadableException{
        // should not be called, since we override read instead
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean canRead(Type type, Class contextClass, MediaType mediaType) {
        return can(type,contextClass,mediaType,requestMediaTypes,"canRead");
    }

    @Override
    public boolean canWrite(Type type, Class contextClass, MediaType mediaType) {
        return can(type,contextClass,mediaType,responseMediaTypes,"canWrite");
    }
    private boolean can(Type type, Class contextClass, MediaType mediaType,List<MediaType> mediaTypes,String can){
        String uri=this.request().getRequestURI();
        if(log.isDebugEnabled()){
            log.debug("EncryptionHttpMessageConverter."+can+":"+mediaType+";"+this.requestMediaTypes+';'+uri);
        }
        if(Help.isNotEmpty(mediaTypes)){
            for(int i=0,l=mediaTypes.size();i<l;i++){
                if(mediaTypes.get(i).includes(mediaType)){
                    return true;
                }
            }
            return false;
        }else{
            return false;
        }
    }
    @Override
    protected boolean supports(Class<?> clazz){
     // should not be called, since we override canRead/Write instead
        throw new UnsupportedOperationException();
    }
    
}
