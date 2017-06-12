package com.netty.client;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

/**
 * 将业务系统产生的消息推向Netty服务器
 * 
 * @author cuizhanyou
 * @date 2014年2月14日
 * @version 1.0
 */
public class InitDataClientHandler extends ChannelHandlerAdapter {

	private static final Logger logger = Logger.getLogger(InitDataClientHandler.class.getName());
	private String msg_json;
	
	public InitDataClientHandler(String msg) {
		this.msg_json = msg;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		logger.info(getFormateDate()+"业务系统模拟器杜撰业务消息");
		ctx.writeAndFlush(Unpooled.copiedBuffer(msg_json.getBytes()));
	}
	
	/**
	 * 每当从客户端收到新的数据时，这个方法会在收到消息时被调用
	 */
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		logger.info(getFormateDate()+"业务系统模拟器接收服务器返回的消息");
		//TimeClient中添加LineBasedFrameDecoder、StringDecoder两个解码器后，拿到的msg已经是解码成字符串之后的应答消息了
		String body = (String) msg;
		logger.info("client 服务器返回的应答信息 : " + body);
		ctx.close();
	}

	/**
	 * 当Netty由于IO错误或者处理器在处理事件时抛出的异常时会被调用
	 * 在大部分情况下，捕获的异常应该被记录下来并且把关联的channel给关闭掉
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		logger.info(getFormateDate()+"业务系统模拟器发生异常");
		// 释放资源
		cause.printStackTrace();
		ctx.close();
	}
	
	private static String getFormateDate(){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(new Date())+"-----";
	}
}
