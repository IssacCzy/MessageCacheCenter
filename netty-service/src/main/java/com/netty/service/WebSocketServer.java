package com.netty.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netty.PropertiesUtil;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * @author cuizhanyou
 * @date 2014年2月14日
 * @version 1.0
 */
public class WebSocketServer {
	
	/*
	 * 将Channel通道注册到ChannelGroup来管理
	 * 当客户端发出关闭链路请求时，将Channel通道从该group中remove掉
	 */
	public static ChannelGroup channelGroup = new DefaultChannelGroup("ws-acceptor-groups", GlobalEventExecutor.INSTANCE);
	
	//服务器接收业务系统消息的端口侦听
	private static final int ReceiveMsgPost = Integer.valueOf(PropertiesUtil.getProperty("netty.service.receive.businessSysMsg.post"));
	//服务器推送消息给浏览器的端口侦听
	private static final int PushMsgPost = Integer.valueOf(PropertiesUtil.getProperty("netty.service.push.browser.post"));
	
	
	/*
	 * HashMap<UserId,Map<type,List<ChannelId>>  后期：ChannelGroup    一个用户对应多个模块，可能多个浏览器，so多个channel
	 * 维护用户、(模块)类型type和Channel通道之间的对应关系
	 */
	public static Map<String,Map<String,List<ChannelId>>> channel2User = new HashMap<String,Map<String,List<ChannelId>>>();
	
	//HashMap<type,HashMap<uId,List<json_data>>>
	//info_queue_1:  0<= uId%1000 <=199
	//info_queue_2:  200<= uId%1000 <=399
	//info_queue_3:  400<= uId%1000 <=599
	//info_queue_4:  600<= uId%1000 <=799
	//info_queue_5:  800<= uId%1000 <=999
	public static Map<String,Map<String,List<String>>> info_queue_1 = new HashMap<String,Map<String,List<String>>>();
	public static Map<String,Map<String,List<String>>> info_queue_2 = new HashMap<String,Map<String,List<String>>>();
	public static Map<String,Map<String,List<String>>> info_queue_3 = new HashMap<String,Map<String,List<String>>>();
	public static Map<String,Map<String,List<String>>> info_queue_4 = new HashMap<String,Map<String,List<String>>>();
	public static Map<String,Map<String,List<String>>> info_queue_5 = new HashMap<String,Map<String,List<String>>>();
	
	public void run() throws Exception {
		/*
		 * 定义Netty的Reactor线程池 EventLoopGroup，它实际上是 EventLoop的数组
		 * EventLoop的职责是：处理所有注册到本线程多路复用器Selector上的Channel
		 * 
		 * 创建客户端处理IO读写的 NioEventLoopGroup线程组
		 * 配置服务端的NIO线程组    NioEventLoopGroup是个线程组，它包含了一组NIO线程，专门用于网络事件的处理
		 * 实际上他们就是 Reactor 线程组
		 * 
		 * 创建两个的原因：一个用于服务器端接收客户端的连接；一个用于 SocketChannel的网络读写
		 * bossGroup用于所有channel:用来接收进来的连接
		 * workGroup则应用于某个channel:用来处理已经被接收的连接
		 * 
		 * 一旦bossGroup接收到连接，就会把连接信息注册到workGroup上
		 */
		EventLoopGroup bossGroup = new NioEventLoopGroup();	//NioEventLoopGroup 是用来处理I/O操作的多线程事件循环器
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		
		EventLoopGroup bossGroup2 = new NioEventLoopGroup();
		EventLoopGroup workerGroup2 = new NioEventLoopGroup();
		try {
			/*
			 * 它是Netty用于启动NIO服务器端的辅助启动类，目的是降低服务器端的开发复杂度
			 * 规定动作ServerBootstrap，group，channel以及handler等等，这些代码都非常模式化，不需要多做说明
			 * 
			 * 针对创建ServerBootstrap的构造函数竟然不需要参数的说明：
			 * 		由于 ServerBootstrap 的参数过多，并且未来也可能发生变化，为了解决此问题就引入了 Builder 模式
			 * 		仔细看一下程序 b.method();  每个method都是返回一个ServerBootstrap
			 * 
			 * 		注意引申学习 Builder 模式，方便针对属性较多的类一步步构造一个实例对象
			 */
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)   //1、完成线程组和线程类型的设置：将两个NIO线程加入到辅助启动类中
				/*
				 * 2、设置并绑定服务端Channel
				 * 3、TCP参数设置（这里没有实现）；
				 * 设置创建的 Channel为 NioServerSocketChannel，它的功能对应于JDK NIO类库中的ServerSocketChannel类
				 * 
				 * Netty 是通过Channel工厂来创建不同类型的Channel,服务端需要的Channel 为 NioServerSocketChannel
				 */
				.channel(NioServerSocketChannel.class)
				/*
				 * 4、最后为启动辅助类绑定Handler事件处理类。
				 * 类似Reactor中的handler处理类，主要用于处理网络IO事件
				 * 链路建立的时候需要创建并初始化ChannelPipeline：其本质是负责处理网络事件的职责链
				 * 
				 * 该Handler是NioServerSocketChannel使用的
				 */
				.childHandler(new ChannelInitializer<SocketChannel>() {	//链路建立的时候需要创建并初始化ChannelPipeline：其本质是负责处理网络事件的职责链
					
						@Override
						protected void initChannel(SocketChannel ch) throws Exception {
							ChannelPipeline pipeline = ch.pipeline();
							//添加HttpServerCodec：将请求/应答消息编码/解码成HTTP消息
							pipeline.addLast("http-codec", new HttpServerCodec());
							//添加HttpObjectAggregator的目的是将HTTP消息的多个组成部分组合成一条完整的HTTP消息
							pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
							//添加ChunkedWriteHandler来向客户端发送HTML5文件，它主要用于支持浏览器和服务器端进行WebSocket通信 
							pipeline.addLast("http-chunked", new ChunkedWriteHandler());
							//添加业务处理类
							pipeline.addLast("handler", new WebSocketServerHandler());
						}
						
					});

			/*
			 * 5、 绑定监听端口，调用同步阻塞方法sync等待绑定操作完成， 启动服务
			 */
			//完成后Netty会返回一个ChannelFuture，用于异步操作的通知回调
			ChannelFuture future = b.bind(PushMsgPost).sync();  
			System.out.println("客户端向服务器发送socket请求的监听端口： " + PushMsgPost);
			
			ServerBootstrap b2 = new ServerBootstrap();
			b2.group(bossGroup2, workerGroup2)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_BACKLOG, 1024)
				.childHandler(new ChannelInitializer<SocketChannel>() {	//链路建立的时候需要创建并初始化ChannelPipeline：其本质是负责处理网络事件的职责链
					
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						//在TimeServerHandler之前添加两个解码器
						ByteBuf delimiter = Unpooled.copiedBuffer("$_".getBytes());
						ch.pipeline().addLast(new DelimiterBasedFrameDecoder(1024, delimiter));
						ch.pipeline().addLast(new StringDecoder());
						ch.pipeline().addLast(new ReceiveBusinessSysDataHandler());
					}
					
				});
			// 绑定端口，同步等待成功
			ChannelFuture future2 = b2.bind(ReceiveMsgPost).sync();
			System.out.println("业务系统向服务器推送消息的监听端口： " + ReceiveMsgPost);
			
			// 等待服务端监听端口关闭
			// 使用下面方法进行阻塞，等待服务端监听的端口链路关闭后main函数才退出
			future2.channel().closeFuture().sync();
			// 使用下面方法进行阻塞，等待服务端监听的端口链路关闭后main函数才退出
			future.channel().closeFuture().sync();
			
		} finally {
			// 优雅退出，释放线程池资源
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
			
			// 优雅退出，释放线程池资源
			bossGroup2.shutdownGracefully();
			workerGroup2.shutdownGracefully();
		}
	}

	public static void main(String[] args) throws Exception {
		new WebSocketServer().run();
	}
	

}
