package com.wifiin.nio.netty;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.google.common.collect.Maps;

import com.google.common.collect.Lists;
import com.wifiin.nio.OutputObject;
import com.wifiin.nio.exception.NioException;
import com.wifiin.nio.netty.channel.ChannelConnectChecker;
import com.wifiin.nio.netty.channel.codec.AbstractCommonCodec;
import com.wifiin.reflect.ClassForNameMap;
import com.wifiin.util.Help;
import com.wifiin.util.MachineUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;

/**
 * 客户端与服务端通用参数
 * @author Running
 */
public class NettyGeneralParams<I,O extends OutputObject,T extends AbstractCommonCodec<I,O>> implements Serializable{
    /**
     * 
     */
    private static final long serialVersionUID=3183906794959020556L;
    private static final int DEFAULT_THREADS=Runtime.getRuntime().availableProcessors()*2;
    private int selectorThreads=DEFAULT_THREADS;
    private int channelHandlerThreads=DEFAULT_THREADS;
    @SuppressWarnings("rawtypes")
    private Map<ChannelOption,Object> channelOptions=Maps.newHashMap();
    private int maxIdleSeconds=60;
    private List<Class<T>> channelHandlers;
    private Class<ChannelConnectChecker> channelConnectChecker;
    private Class<ChannelConnectChecker> aterConnectingChecker;
    private Class<Runnable> runnableBeforeClosing;
    private int port=8112;
    private int maxBusinessThreads=500;
    private long businessThreadIdleSeconds=60;
    private int businessTaskQueueLength=1;
    public NettyGeneralParams(){
        NettyContext.registerParams(this);
    }
    public void port(int port){
        this.port=port;
    }
    public int port(){
        return port;
    }
    public void businessThreads(int threads){
        maxBusinessThreads=threads;
    }
    public void businessThreadIdleSeconds(long seconds){
        businessThreadIdleSeconds=seconds;
    }
    public void businessTaskQueueLength(int length){
        businessTaskQueueLength=length;
    }
    public void tcpNoDelay(boolean tcpNoDelay){
        addChannelOption(ChannelOption.TCP_NODELAY,tcpNoDelay);
    }
    public void keepalive(boolean keepalive){
        addChannelOption(ChannelOption.SO_KEEPALIVE,keepalive);
    }
    public void sndBuf(int sndBuf){
        addChannelOption(ChannelOption.SO_SNDBUF,sndBuf);
    }
    public void rcvBuf(int rcvBuf){
        addChannelOption(ChannelOption.SO_RCVBUF,rcvBuf);
    }
    protected <T> void addChannelOption(ChannelOption<T> option,Object value){
        channelOptions.put(option,value);
    }
    @SuppressWarnings("rawtypes")
    public Map<ChannelOption,Object> channelOptions(){
        return Collections.unmodifiableMap(channelOptions);
    }
    
    public void maxIdleSeconds(int seconds){
        this.maxIdleSeconds=seconds;
    }
    public int maxIdleSeconds(){
        return maxIdleSeconds;
    }
    
    public void addChannelHandlerClass(Class<T> channelHandler){
        if(channelHandlers==null){
            channelHandlers=Lists.newArrayList();
        }
        channelHandlers.add(channelHandler);
    }
    public void addChannelHandlerClass(String encoderClassName){
        addChannelHandlerClass(ClassForNameMap.get(encoderClassName));
    }
    private <T> T newInstance(Class<T> c,T insteadOf){
        if(c!=null){
            try{
                return c.newInstance();
            }catch(InstantiationException | IllegalAccessException e){
                throw new NioException(e);
            }
        }
        return insteadOf;
    }
    @SuppressWarnings("unchecked")
    public AbstractCommonCodec<I,O>[] channelHandlers(){
        if(Help.isNotEmpty(this.channelHandlers)){
            AbstractCommonCodec<I,O>[] channelHandlers=new AbstractCommonCodec[this.channelHandlers.size()];
            for(int i=0,l=this.channelHandlers.size();i<l;i++){
                    channelHandlers[i]=newInstance(this.channelHandlers.get(i),null);
            }
            return channelHandlers;
        }
        return new AbstractCommonCodec[0];
    }
    
    public void idleChannelChecker(Class<ChannelConnectChecker> checkerClass){
        this.channelConnectChecker=checkerClass;
    }
    public void idleChannelChecker(String checkClassName){
        idleChannelChecker(ClassForNameMap.get(checkClassName));
    }
    public ChannelConnectChecker channelConnectChecker(){
        return newInstance(channelConnectChecker,(ch)->{return false;});
    }
    
    private int threadCount(int threads){
        return threads<=0?DEFAULT_THREADS:threads;
    }
    public void afterConnectingChecker(String checker){
        afterConnectingChecker(ClassForNameMap.get(checker));
    }
    public void afterConnectingChecker(Class<ChannelConnectChecker> checker){
        aterConnectingChecker=checker;
    }
    public ChannelConnectChecker afterConnectingChecker(){
        return newInstance(aterConnectingChecker,(ch)->{return true;});
    }
    public void runnableBeforeClosing(String runnable){
        runnableBeforeClosing(ClassForNameMap.get(runnable));
    }
    public void runnableBeforeClosing(Class<Runnable> runnable){
        this.runnableBeforeClosing=runnable;
    }
    public Runnable runnableBeforeClosing(){
        return newInstance(runnableBeforeClosing,()->{});
    }
    public DefaultEventExecutorGroup newEventExecutorGroup(String groupName){
        return new DefaultEventExecutorGroup(threadCount(channelHandlerThreads),newThreadFactory(groupName));
    }
    public EventLoopGroup newEventLoopGroup(String groupName){
        return newEventLoopGroup(groupName,selectorThreads);
    }
    protected EventLoopGroup newEventLoopGroup(String groupName,int threadCount){
        if(MachineUtil.isLinux()){
            return new EpollEventLoopGroup(threadCount(threadCount),newThreadFactory(groupName));
        }else{
            return new NioEventLoopGroup(threadCount(threadCount),newThreadFactory(groupName));
        }
    }
    public ExecutorService newBusinessExecutorService(String threadName){
        return new ThreadPoolExecutor(1,this.maxBusinessThreads,businessThreadIdleSeconds,TimeUnit.SECONDS,
                businessTaskQueueLength<=0?new SynchronousQueue<Runnable>():new ArrayBlockingQueue<Runnable>(businessTaskQueueLength),
                newThreadFactory(threadName),new ThreadPoolExecutor.AbortPolicy());
    }
    
    private ThreadFactory newThreadFactory(String threadName){
        AtomicInteger tidx=new AtomicInteger(0);
        return (r)->{
            return new Thread(r,threadName+"-"+tidx.incrementAndGet());
        };
    }
    
}
