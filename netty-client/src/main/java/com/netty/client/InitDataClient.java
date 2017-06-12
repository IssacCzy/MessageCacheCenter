package com.netty.client;

import java.util.logging.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 * 业务系统消息生成器：
 * 		模仿业务系统，向Netty服务器推送业务消息
 * 
 * @author cuizhanyou
 * @date 2016-05-23
 * @version 1.0
 */
public class InitDataClient {
	
	private static final Logger logger = Logger.getLogger(InitDataClient.class.getName());
	//netty 消息中心的 host
	private static final String HOST = PropertiesUtil.getProperty("netty.service.host");
	//netty 消息中心侦听的接收消息的端口
	private static final int POST = Integer.parseInt(PropertiesUtil.getProperty("netty.service.post"));
	
	/**
	 * 用于业务系统推送消息至netty消息中心
	 * @param msg_json:业务系统消息
	 * @throws Exception
	 */
	public void sendMsg(String msg_json) throws Exception {
		
		logger.info("message content from business sys : "+msg_json);
		/*
		 * 创建客户端处理IO读写的 NioEventLoopGroup线程组
		 * 客户端 Bootstrap 和 服务器端 ServerBootstrap 类似，也适用 Builder 模式
		 * 对于客户端来说，它不需要监听和处理来自客户端的连接，所以只需要一个Reactor线程组即可
		 */
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			//创建客户端辅助启动类
			Bootstrap b = new Bootstrap();
			b.group(group)		//将NIO线程加入到辅助启动类中
				.channel(NioSocketChannel.class)	//设置创建的 Channel为 NioSocketChannel
				.option(ChannelOption.TCP_NODELAY, true)	//配置NioSocketChannel的TCP参数
				.handler(new ChannelInitializer<SocketChannel>() {		//最后通过内部类的方式绑定Handler事件处理类
						
						/**
						 * 其作用是：当创建NioSocketChannel成功之后，在初始化它的时候将它的 ChannelHandler设置到ChannelPipeline中，
						 * 用于处理网络IO事件
						 */
						@Override
						public void initChannel(SocketChannel ch) throws Exception {
							
							ByteBuf delimiter = Unpooled.copiedBuffer("$_".getBytes());
							ch.pipeline().addLast(new DelimiterBasedFrameDecoder(1024, delimiter));
							ch.pipeline().addLast(new StringDecoder());
//							ch.pipeline().addLast(new InitDataClientHandler("{\"uId\":\"1001\",\"type\":\"memo\",\"data\":{\"title\":\"memo最新工作进展记录\",\"content\":\"今天开始动工\",\"timeStamp\":\"1463728695871\"}}$_"));
							ch.pipeline().addLast(new InitDataClientHandler(msg_json));
						}
					});

			
			logger.info("netty service host : "+HOST);
			logger.info("netty service post : "+POST);
			// 发起异步连接操作
			ChannelFuture f = b.connect(HOST, POST).sync();

			// 当代客户端链路关闭
			f.channel().closeFuture().sync();
		} finally {
			// 优雅退出，释放NIO线程组
			group.shutdownGracefully();
		}
	}
	
	
}
